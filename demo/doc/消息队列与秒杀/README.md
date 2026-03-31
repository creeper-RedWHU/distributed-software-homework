# 消息队列与秒杀系统

## 文档目录

| 文档 | 说明 | 优先级 |
|------|------|--------|
| [01-Kafka异步秒杀.md](01-Kafka异步秒杀.md) | Kafka 削峰填谷、Lua脚本防超卖、幂等性保障 | 必读 |
| [02-雪花算法与分库分表.md](02-雪花算法与分库分表.md) | 雪花算法、基因法、ShardingSphere 分表配置 | 必读 |
| [03-Redis预扣减与一致性保障.md](03-Redis预扣减与一致性保障.md) | Redis 限购预扣减、下单补偿、支付状态一致性 | 必读 |

## 完整技术栈

```
┌────────────────────────────────────────────────────┐
│                    Nginx 负载均衡                    │
├────────────────────────────────────────────────────┤
│              Spring Boot 应用 (多实例)               │
│  ┌─────────┐  ┌──────────┐  ┌───────────────────┐  │
│  │  Shiro  │  │  Redis   │  │  ShardingSphere   │  │
│  │  认证   │  │  Lua脚本 │  │  分库分表          │  │
│  └─────────┘  └──────────┘  └───────────────────┘  │
├────────────────────────────────────────────────────┤
│  ┌──────────┐  ┌───────────┐  ┌────────────────┐   │
│  │  Kafka   │  │   MySQL   │  │ ElasticSearch  │   │
│  │  消息队列│  │  读写分离  │  │   商品搜索     │   │
│  └──────────┘  └───────────┘  └────────────────┘   │
└────────────────────────────────────────────────────┘
```

## 快速验证

```bash
# 1. 启动全部服务
cd demo
docker-compose -f docker-compose.full.yml up -d --build

# 2. 预热秒杀库存
curl -X POST http://localhost/api/seckill/1/warm-stock

# 3. 执行秒杀
curl -X POST "http://localhost/api/seckill/do?userId=1&seckillId=1"

# 4. 查询订单状态
curl http://localhost/api/seckill/order/status/{返回的orderId}

# 5. 支付订单
curl -X POST "http://localhost/api/seckill/order/{返回的orderId}/pay?userId=1"

# 6. 查询用户订单
curl http://localhost/api/seckill/order/user/1

# 7. 验证幂等性（重复秒杀被拒绝）
curl -X POST "http://localhost/api/seckill/do?userId=1&seckillId=1"

# 8. 并发压测（100用户抢20库存）
for i in $(seq 1 100); do
  curl -s -X POST "http://localhost/api/seckill/do?userId=$i&seckillId=3" &
done
wait

# 9. 查看分表数据分布
docker exec seckill-mysql-master mysql -uroot -proot seckill_db \
  -e "SELECT 't_seckill_order_0' AS tbl, COUNT(*) FROM t_seckill_order_0 UNION ALL SELECT 't_seckill_order_1', COUNT(*) FROM t_seckill_order_1;"
```

## API 接口

| 方法 | 接口 | 说明 |
|------|------|------|
| POST | `/api/seckill/do?userId=&seckillId=` | 执行秒杀（异步，返回orderId） |
| POST | `/api/seckill/order/{orderId}/pay?userId=` | 异步支付订单 |
| GET | `/api/seckill/order/status/{orderId}` | 轮询订单状态 |
| GET | `/api/seckill/order/{orderId}` | 按订单ID查询订单 |
| GET | `/api/seckill/order/user/{userId}` | 按用户ID查询订单列表 |
| GET | `/api/seckill/list` | 秒杀商品列表 |
| GET | `/api/seckill/{id}` | 秒杀商品详情 |
| POST | `/api/seckill/{id}/warm-stock` | 预热秒杀库存到Redis |
