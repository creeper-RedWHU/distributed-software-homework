package com.example.demo.service;

import com.example.demo.common.Constants;
import com.example.demo.mapper.SeckillActivityMapper;
import com.example.demo.mapper.StockLogMapper;
import com.example.demo.model.entity.SeckillActivity;
import com.example.demo.model.entity.StockLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final DefaultRedisScript<Long> seckillDeductScript;
    private final SeckillActivityMapper activityMapper;
    private final StockLogMapper stockLogMapper;

    /**
     * 库存预热：从 MySQL 加载到 Redis
     */
    public void warmupStock(Long activityId) {
        SeckillActivity activity = activityMapper.selectById(activityId);
        if (activity == null) return;

        String stockKey = Constants.REDIS_STOCK_KEY + activityId;
        redisTemplate.opsForValue().set(stockKey, activity.getAvailableStock(),
                2, TimeUnit.HOURS);
        log.info("库存预热完成: activityId={}, stock={}", activityId, activity.getAvailableStock());
    }

    /**
     * Redis Lua 原子扣减库存
     * @return >0 剩余库存, -1 库存不足, -2 超出限购
     */
    public Long deductStockRedis(Long activityId, Long userId, int limitPerUser, int quantity) {
        String stockKey = Constants.REDIS_STOCK_KEY + activityId;
        String boughtKey = Constants.REDIS_BOUGHT_KEY + activityId + ":" + userId;

        return redisTemplate.execute(seckillDeductScript,
                List.of(stockKey, boughtKey),
                limitPerUser, quantity);
    }

    /**
     * Redis 库存回滚
     */
    public void rollbackStockRedis(Long activityId, Long userId, int quantity) {
        String stockKey = Constants.REDIS_STOCK_KEY + activityId;
        String boughtKey = Constants.REDIS_BOUGHT_KEY + activityId + ":" + userId;

        redisTemplate.opsForValue().increment(stockKey, quantity);
        redisTemplate.opsForValue().decrement(boughtKey, quantity);
        log.info("Redis库存回滚: activityId={}, quantity={}", activityId, quantity);
    }

    /**
     * MySQL 乐观锁扣减库存（Kafka 消费者调用）
     */
    public boolean deductStockMySQL(Long activityId, int quantity) {
        SeckillActivity activity = activityMapper.selectById(activityId);
        if (activity == null) return false;

        int rows = activityMapper.deductStockOptimistic(activityId, quantity, activity.getVersion());
        if (rows > 0) {
            log.info("MySQL库存扣减成功: activityId={}, quantity={}", activityId, quantity);
            return true;
        }
        log.warn("MySQL库存扣减失败(乐观锁冲突): activityId={}", activityId);
        return false;
    }

    /**
     * MySQL 库存回滚
     */
    public void rollbackStockMySQL(Long activityId, int quantity) {
        activityMapper.rollbackStock(activityId, quantity);
        log.info("MySQL库存回滚: activityId={}, quantity={}", activityId, quantity);
    }

    /**
     * 记录库存流水
     */
    public void logStockChange(Long activityId, String orderNo, Long userId, int quantity, int type) {
        StockLog stockLog = new StockLog();
        stockLog.setActivityId(activityId);
        stockLog.setOrderNo(orderNo);
        stockLog.setUserId(userId);
        stockLog.setQuantity(type == Constants.STOCK_LOG_DEDUCT ? -quantity : quantity);
        stockLog.setType(type);
        stockLogMapper.insert(stockLog);
    }

    /**
     * 获取 Redis 库存
     */
    public Integer getRedisStock(Long activityId) {
        Object stock = redisTemplate.opsForValue().get(Constants.REDIS_STOCK_KEY + activityId);
        return stock != null ? Integer.parseInt(stock.toString()) : null;
    }
}
