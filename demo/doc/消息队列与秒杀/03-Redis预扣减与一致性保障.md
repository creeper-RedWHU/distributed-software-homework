# Redis 预扣减与一致性保障改造

## 1. 改造目标

围绕“订单服务”和“库存服务”两个独立微服务场景，在当前 Spring Boot 单仓项目中补齐以下能力：

1. 秒杀下单时基于 Redis 完成库存预扣减，防超卖、限购。
2. 使用消息驱动的一致性方案保障：
   - 下单 + 库存扣减一致性
   - 订单支付 + 订单状态更新一致性
3. 补充可离线执行的单元测试，避免测试依赖 MySQL/Redis/Kafka 真环境。

## 2. 当前实现落点

### 2.1 Redis 原子预扣减

实现类：

- `src/main/java/com/example/demo/service/SeckillRedisService.java`
- `src/main/java/com/example/demo/service/SeckillService.java`

Lua 脚本在一次 Redis 调用中完成：

1. 检查库存是否存在且大于 0。
2. 检查用户是否已经提交过本场秒杀订单。
3. 检查限购计数是否超限。
4. 原子预减库存。
5. 原子增加用户限购计数。
6. 写入处理中标记。

返回码约定：

- `0`: 预扣减成功
- `1`: 重复下单
- `2`: 库存不足
- `3`: 超过限购

说明：

- 当前接口仍按“单次秒杀 1 件商品”处理，因此数据库唯一索引与 Redis 标记共同保证每用户每场活动 1 单。
- 新增了 `purchase_limit` 字段，为后续扩展“单单多件”保留配置位。

## 3. 下单 + 库存扣减一致性

### 3.1 处理链路

1. `SeckillService#doSeckill`
   - 校验活动时间
   - 执行 Redis Lua 预扣减
   - 生成雪花订单号
   - 发送 Kafka 下单消息
   - 如果消息发送失败，立即回补 Redis

2. `SeckillOrderConsumer`
   - 消费下单消息
   - 委托 `SeckillOrderProcessService#processCreateOrder`

3. `SeckillOrderProcessService`
   - 检查订单是否已存在，做幂等处理
   - 扣减秒杀库存表
   - 扣减商品库存表
   - 创建订单，状态置为 `PENDING_PAYMENT`
   - 写回 Redis 订单状态

### 3.2 失败补偿

当任一环节失败时，执行补偿：

- Kafka 发送失败：
  - 回补 Redis 预扣减库存
  - 清理订单占位标记

- 秒杀库存扣减失败：
  - 回补 Redis
  - 订单状态置为 `FAILED`

- 商品库存扣减失败：
  - 回补数据库秒杀库存
  - 回补 Redis

- 订单落库异常：
  - 回补商品库存
  - 回补秒杀库存
  - 回补 Redis

### 3.3 幂等性

幂等由四层共同保证：

1. Redis 订单占位键：防重复请求。
2. Redis 限购计数：防超限提交。
3. 数据库唯一索引 `uk_user_seckill(user_id, seckill_id)`。
4. 消费端查重 + 重复消息补偿处理。

## 4. 支付 + 订单状态更新一致性

### 4.1 处理链路

新增接口：

```bash
POST /api/seckill/order/{orderId}/pay?userId={userId}
```

处理流程：

1. `SeckillService#payOrder`
   - 校验订单存在
   - 校验用户与订单匹配
   - 校验订单必须处于 `PENDING_PAYMENT`
   - 发送 Kafka 支付消息

2. `SeckillOrderPayConsumer`
   - 消费支付消息
   - 委托 `SeckillOrderPaymentService#processPayOrder`

3. `SeckillOrderPaymentService`
   - 幂等读取订单
   - 通过条件更新 `status: 0 -> 1`
   - 将 Redis 状态更新为 `PAID`

### 4.2 支付幂等

支付链路具备以下幂等能力：

- 订单已支付时，重复消息直接视为成功。
- 并发更新时，若条件更新返回 `0`，会重新读取订单状态。
- 如果其他消费者已将订单改为 `PAID`，本次消费仍按成功处理。

## 5. 数据结构变更

### 5.1 秒杀商品表

新增字段：

```sql
purchase_limit INT NOT NULL DEFAULT 1
```

### 5.2 订单状态语义

订单表状态：

- `0`: `PENDING_PAYMENT`
- `1`: `PAID`
- `2`: `CANCELLED`
- `3`: `CREATE_FAILED`

Redis 轮询状态：

- `PROCESSING`
- `PENDING_PAYMENT`
- `PAID`
- `FAILED`
- `UNKNOWN`

## 6. 测试覆盖

新增单元测试：

- `src/test/java/com/example/demo/service/SeckillServiceTest.java`
  - 秒杀成功
  - Kafka 发送失败回补 Redis
  - Redis 状态缺失时回查 DB
  - 支付消息发送

- `src/test/java/com/example/demo/service/SeckillOrderProcessServiceTest.java`
  - 正常下单
  - 秒杀库存不足补偿
  - 订单落库失败补偿
  - 重复消息幂等处理

- `src/test/java/com/example/demo/service/SeckillOrderPaymentServiceTest.java`
  - 订单不存在
  - 正常支付
  - 已支付幂等
  - 并发更新回查成功

同时将原 `DemoApplicationTests` 改为不依赖外部中间件的轻量 smoke test。

## 7. 验证建议

### 7.1 单元测试

```bash
./mvnw test
```

### 7.2 集成验证

```bash
# 1. 启动完整环境
docker-compose -f docker-compose.full.yml up -d --build

# 2. 预热库存
curl -X POST http://localhost/api/seckill/1/warm-stock

# 3. 发起秒杀
curl -X POST "http://localhost/api/seckill/do?userId=1&seckillId=1"

# 4. 轮询订单状态
curl http://localhost/api/seckill/order/status/{orderId}

# 5. 支付订单
curl -X POST "http://localhost/api/seckill/order/{orderId}/pay?userId=1"

# 6. 再次轮询，期望看到 PAID
curl http://localhost/api/seckill/order/status/{orderId}
```

## 8. 当前限制

1. 当前仓库仍是单应用部署，文档中的“订单服务/库存服务”边界通过消息处理类和服务拆分来表达。
2. `purchase_limit` 已进入模型和 Redis 校验，但当前下单接口仍固定为 1 件。
3. 若要完全对齐独立微服务生产形态，下一步应补本地消息表 / Outbox 或引入事务消息。
