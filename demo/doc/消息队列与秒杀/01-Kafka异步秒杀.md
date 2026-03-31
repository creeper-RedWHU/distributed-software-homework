# Kafka 消息队列 - 异步秒杀下单

## 一、架构概述

```
用户请求
   │
   ▼
┌──────────────────────────────────────────────────────┐
│                   秒杀接口层                          │
│  1. Redis Lua脚本: 原子性检查重复 + 预减库存           │
│  2. 雪花算法生成订单ID                                │
│  3. 发送Kafka消息（异步）                             │
│  4. 立即返回orderId给客户端                           │
└──────────────┬───────────────────────────────────────┘
               │ Kafka消息
               ▼
┌──────────────────────────────────────────────────────┐
│              Kafka Broker (削峰填谷)                  │
│  Topic: seckill-order, 4 Partitions                  │
│  按userId分区，保证同一用户的消息顺序消费               │
└──────────────┬───────────────────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────────────────┐
│              订单消费者 (SeckillOrderConsumer)         │
│  1. 幂等检查：DB中是否已存在订单                       │
│  2. 数据库扣减秒杀库存（乐观锁 stock > 0）            │
│  3. 数据库扣减商品库存                                │
│  4. 创建订单记录（PENDING_PAYMENT）                   │
│  5. 更新Redis订单状态: PROCESSING → PENDING_PAYMENT   │
└──────────────────────────────────────────────────────┘
```

**核心思想**: 将秒杀高并发写请求通过 Kafka 削峰填谷，前端快速响应，后端异步处理。

## 二、核心实现

### 2.1 关键类

| 类 | 作用 |
|---|------|
| `KafkaConfig` | Kafka Topic 配置（seckill-order / seckill-order-pay） |
| `SeckillOrderProducer` | 发送秒杀订单消息到 Kafka |
| `SeckillOrderConsumer` | 消费Kafka消息，异步扣库存并创建订单 |
| `SeckillOrderPayProducer` | 发送订单支付消息到 Kafka |
| `SeckillOrderPayConsumer` | 消费支付消息并更新订单状态 |
| `SeckillOrderMessage` | Kafka消息体（orderId, userId, seckillId, productId, price） |
| `SeckillOrderPayMessage` | Kafka 支付消息体（orderId, userId, payTime） |
| `SeckillService.doSeckill()` | 秒杀入口：Redis预减库存 + 发送Kafka消息 |

### 2.2 Redis Lua 脚本（原子性防超卖）

```lua
-- KEYS[1] = 订单标记key  KEYS[2] = 库存key
-- 返回: 0=成功, 1=重复下单, 2=库存不足

if redis.call('exists', KEYS[1]) == 1 then return 1 end   -- 重复下单检查
local stock = redis.call('decr', KEYS[2])                  -- 预减库存
if stock < 0 then redis.call('incr', KEYS[2]) return 2 end -- 库存不足回滚
redis.call('set', KEYS[1], 'PROCESSING', 'EX', 86400)      -- 标记处理中
return 0                                                     -- 成功
```

**为什么用Lua脚本？**
Redis 单线程执行 Lua 脚本，保证"检查重复 + 扣减库存 + 标记订单"三个操作的原子性，避免并发下的竞态条件。

### 2.3 秒杀流程代码

```java
// SeckillService.doSeckill() 核心逻辑
public Result<Long> doSeckill(Long userId, Long seckillId) {
    // 1. 校验秒杀时间
    // 2. Redis Lua脚本: 原子性检查重复 + 预减库存
    DefaultRedisScript<Long> script = new DefaultRedisScript<>(SECKILL_LUA_SCRIPT, Long.class);
    Long result = redisTemplate.execute(script, Arrays.asList(orderKey, stockKey));

    // 3. 雪花算法生成订单ID（基因法嵌入userId）
    long orderId = snowflakeIdGenerator.nextOrderId(userId);

    // 4. 发送Kafka消息异步处理
    SeckillOrderMessage message = new SeckillOrderMessage(orderId, userId, seckillId, ...);
    seckillOrderProducer.sendSeckillOrder(message);

    // 5. 立即返回orderId，客户端轮询状态
    return Result.success(orderId);
}
```

### 2.4 Kafka消费者（异步创建订单）

```java
@KafkaListener(topics = "seckill-order", groupId = "seckill-order-group")
@Transactional(rollbackFor = Exception.class)
public void consumeSeckillOrder(SeckillOrderMessage message) {
    // 1. 幂等检查：DB中是否已存在
    SeckillOrder existing = seckillOrderMapper.selectByUserAndSeckill(userId, seckillId);
    if (existing != null) return;

    // 2. 数据库扣减库存（乐观锁 WHERE stock > 0）
    int rows = seckillProductMapper.decrStock(seckillId);
    if (rows == 0) { updateOrderStatus(orderId, "FAILED"); return; }

    // 3. 创建订单
    seckillOrderMapper.insertWithId(order);

    // 4. 更新Redis状态
    updateOrderStatus(orderId, "PENDING_PAYMENT");
}
```

## 三、Kafka 配置

### 3.1 application.yml

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      retries: 3
      acks: all    # 所有副本确认，保证消息不丢失
    consumer:
      group-id: seckill-order-group
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: com.example.demo.model.dto
```

### 3.2 Docker 部署

```yaml
# docker-compose.full.yml 中已包含
zookeeper:
  image: confluentinc/cp-zookeeper:7.5.0
  ports: ["2181:2181"]

kafka:
  image: confluentinc/cp-kafka:7.5.0
  ports: ["9092:9092"]
  environment:
    KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092
    KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
```

## 四、幂等性保障

| 层次 | 机制 | 说明 |
|------|------|------|
| **Redis层** | Lua脚本原子检查 | `seckill:order:{seckillId}:{userId}` 标记 |
| **Kafka层** | 按userId分区 | 同一用户的请求顺序消费 |
| **DB层** | UNIQUE索引 | `uk_user_seckill(user_id, seckill_id)` |
| **Consumer层** | 入库前查重 | `selectByUserAndSeckill()` 幂等检查 |

## 五、数据一致性保障

| 问题 | 解决方案 |
|------|----------|
| **超卖** | Redis Lua原子预减 + DB乐观锁 `WHERE stock > 0` |
| **重复下单** | 四层幂等检查（Redis/Kafka/DB索引/Consumer查重） |
| **消息丢失** | Kafka `acks=all` + `retries=3` |
| **消费失败** | `@Transactional` 回滚 + 异常重新抛出触发Kafka重试 |
| **Redis-DB不一致** | Kafka发送失败立即回补Redis，消费失败执行库存补偿并标记FAILED |
| **支付状态一致性** | 支付请求异步化，订单状态通过支付消息条件更新为PAID |

## 六、测试验证

### 6.1 基本秒杀流程

```bash
# 1. 预热库存
curl -X POST http://localhost/api/seckill/1/warm-stock

# 2. 执行秒杀
curl -X POST "http://localhost/api/seckill/do?userId=1&seckillId=1"
# 返回: {"code":200,"data":311778983551307793}  ← orderId

# 3. 轮询订单状态
curl http://localhost/api/seckill/order/status/311778983551307793
# 返回: {"status":"PROCESSING"} → {"status":"PENDING_PAYMENT"}

# 4. 支付订单
curl -X POST "http://localhost/api/seckill/order/311778983551307793/pay?userId=1"

# 5. 查询支付后状态
curl http://localhost/api/seckill/order/status/311778983551307793
# 返回: {"status":"PAID"}

# 6. 查询订单
curl http://localhost/api/seckill/order/311778983551307793
```

### 6.2 幂等性测试

```bash
# 同一用户重复秒杀
curl -X POST "http://localhost/api/seckill/do?userId=1&seckillId=1"
# 返回: {"code":400,"message":"不能重复秒杀"}
```

### 6.3 并发超卖测试

```bash
# 100个用户同时抢20个库存
for i in $(seq 1 100); do
  curl -s -X POST "http://localhost/api/seckill/do?userId=$i&seckillId=3" &
done
wait
# 最多20个成功，其余返回"库存不足"
```

### 6.4 查看Kafka消息

```bash
docker exec seckill-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 --topic seckill-order --from-beginning
```
