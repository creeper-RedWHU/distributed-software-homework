package com.example.demo;

import com.example.demo.util.SnowflakeIdGenerator;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 秒杀系统集成测试
 * 测试内容:
 * 1. 雪花算法ID生成（唯一性、基因法）
 * 2. 分表路由验证（订单ID取模分表）
 * 3. 幂等性验证逻辑
 *
 * 注意: Kafka、Redis、MySQL 相关的集成测试需要在 docker-compose 环境中运行
 * 启动: docker-compose -f docker-compose.full.yml up -d
 * 测试: curl 命令见下方注释
 */
class SeckillIntegrationTest {

    /**
     * 测试雪花算法ID唯一性
     */
    @Test
    void testSnowflakeIdUniqueness() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1, 1);
        Set<Long> ids = new HashSet<>();
        int count = 100000;
        for (int i = 0; i < count; i++) {
            long id = generator.nextId();
            assertTrue(ids.add(id), "ID should be unique: " + id);
        }
        assertEquals(count, ids.size());
        System.out.println("生成 " + count + " 个唯一ID，测试通过");
    }

    /**
     * 测试基因法：订单ID低位嵌入userId哈希
     */
    @Test
    void testSnowflakeGeneMethod() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1, 1);

        for (long userId = 1; userId <= 20; userId++) {
            long orderId = generator.nextOrderId(userId);
            long embeddedUserHash = orderId & 0xF; // 取低4位
            long expectedHash = userId & 0xF;
            assertEquals(expectedHash, embeddedUserHash,
                    "订单ID低4位应嵌入userId哈希: userId=" + userId + ", orderId=" + orderId);
        }
        System.out.println("基因法验证通过：订单ID低4位正确嵌入userId哈希");
    }

    /**
     * 测试分表路由：验证订单ID能正确路由到对应分片
     */
    @Test
    void testShardingRoute() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1, 1);
        int[] shardCount = new int[2]; // 统计两个分片的数量

        for (long userId = 1; userId <= 100; userId++) {
            long orderId = generator.nextOrderId(userId);
            int shard = (int) (orderId % 2); // MOD分表算法
            shardCount[shard]++;
            String tableName = "t_seckill_order_" + shard;
            System.out.printf("userId=%d, orderId=%d, 路由到: %s%n", userId, orderId, tableName);
        }

        System.out.printf("分片0: %d条, 分片1: %d条%n", shardCount[0], shardCount[1]);
        // 验证两个分片都有数据（基因法确保userId影响路由）
        assertTrue(shardCount[0] > 0, "分片0应有数据");
        assertTrue(shardCount[1] > 0, "分片1应有数据");
    }

    /**
     * 测试并发场景下ID生成的唯一性
     */
    @Test
    void testConcurrentIdGeneration() throws InterruptedException {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1, 1);
        Set<Long> allIds = java.util.Collections.synchronizedSet(new HashSet<>());
        int threadCount = 10;
        int idsPerThread = 10000;
        Thread[] threads = new Thread[threadCount];

        for (int t = 0; t < threadCount; t++) {
            threads[t] = new Thread(() -> {
                for (int i = 0; i < idsPerThread; i++) {
                    long id = generator.nextId();
                    assertTrue(allIds.add(id), "并发ID应唯一: " + id);
                }
            });
        }

        for (Thread thread : threads) thread.start();
        for (Thread thread : threads) thread.join();

        assertEquals(threadCount * idsPerThread, allIds.size());
        System.out.println("并发生成 " + allIds.size() + " 个唯一ID，测试通过");
    }
}

/*
 * ================================================================
 * 完整集成测试（需要 docker-compose 环境）
 * ================================================================
 *
 * 1. 启动所有服务:
 *    docker-compose -f docker-compose.full.yml up -d --build
 *
 * 2. 预热秒杀库存:
 *    curl -X POST http://localhost/api/seckill/1/warm-stock
 *
 * 3. 执行秒杀（Kafka异步下单）:
 *    curl -X POST "http://localhost/api/seckill/do?userId=1&seckillId=1"
 *    # 返回 orderId（雪花算法生成）
 *
 * 4. 轮询订单状态:
 *    curl http://localhost/api/seckill/order/status/{orderId}
 *    # 返回 PROCESSING -> SUCCESS/FAILED
 *
 * 5. 查询用户订单:
 *    curl http://localhost/api/seckill/order/user/1
 *
 * 6. 查询订单详情:
 *    curl http://localhost/api/seckill/order/{orderId}
 *
 * 7. 测试幂等性（重复秒杀应被拒绝）:
 *    curl -X POST "http://localhost/api/seckill/do?userId=1&seckillId=1"
 *    # 返回: "不能重复秒杀"
 *
 * 8. 测试超卖防护（并发100个用户秒杀同一商品，库存20个）:
 *    for i in $(seq 1 100); do
 *      curl -s -X POST "http://localhost/api/seckill/do?userId=$i&seckillId=3" &
 *    done
 *    wait
 *    # 最多20个成功，其余返回"库存不足"
 *
 * 9. 验证分表效果（查看MySQL中两个分片表的数据）:
 *    docker exec seckill-mysql-master mysql -uroot -proot seckill_db \
 *      -e "SELECT 't_seckill_order_0' AS tbl, COUNT(*) AS cnt FROM t_seckill_order_0 UNION ALL SELECT 't_seckill_order_1', COUNT(*) FROM t_seckill_order_1;"
 *
 * 10. 查看Kafka消息消费:
 *     docker exec seckill-kafka kafka-console-consumer \
 *       --bootstrap-server localhost:9092 --topic seckill-order --from-beginning
 */
