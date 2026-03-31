package com.example.demo.service;

import com.example.demo.model.entity.SeckillOrder;
import com.example.demo.model.entity.SeckillProduct;
import com.example.demo.model.enums.SeckillOrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class SeckillRedisService {

    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_UNKNOWN = "UNKNOWN";

    private static final String SECKILL_STOCK_KEY = "seckill:stock:";
    private static final String SECKILL_ORDER_KEY = "seckill:order:";
    private static final String SECKILL_ORDER_STATUS_KEY = "seckill:order:status:";
    private static final String SECKILL_LIMIT_KEY = "seckill:limit:";

    /**
     * KEYS[1] = stockKey
     * KEYS[2] = orderKey
     * KEYS[3] = limitKey
     * ARGV[1] = purchaseLimit
     *
     * return 0 success
     * return 1 duplicate
     * return 2 no stock
     * return 3 limit exceeded
     */
    private static final String PRE_DEDUCT_SCRIPT =
            "local stock = tonumber(redis.call('get', KEYS[1]) or '-1') " +
            "if stock < 0 then return 2 end " +
            "if redis.call('exists', KEYS[2]) == 1 then return 1 end " +
            "local limit = tonumber(ARGV[1]) " +
            "local bought = tonumber(redis.call('get', KEYS[3]) or '0') " +
            "if bought >= limit then return 3 end " +
            "if stock <= 0 then return 2 end " +
            "redis.call('decr', KEYS[1]) " +
            "redis.call('incr', KEYS[3]) " +
            "redis.call('expire', KEYS[3], 86400) " +
            "redis.call('set', KEYS[2], '" + STATUS_PROCESSING + "', 'EX', 86400) " +
            "return 0";

    /**
     * KEYS[1] = stockKey
     * KEYS[2] = orderKey
     * KEYS[3] = limitKey
     */
    private static final String RELEASE_SCRIPT =
            "if redis.call('exists', KEYS[2]) == 0 then return 0 end " +
            "redis.call('del', KEYS[2]) " +
            "redis.call('incr', KEYS[1]) " +
            "local bought = tonumber(redis.call('get', KEYS[3]) or '0') " +
            "if bought > 0 then redis.call('decr', KEYS[3]) end " +
            "return 1";

    private final RedisTemplate<String, Object> redisTemplate;

    public PreDeductResult preDeduct(Long userId, SeckillProduct seckillProduct) {
        ensureStockLoaded(seckillProduct);

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(PRE_DEDUCT_SCRIPT, Long.class);
        Long result = redisTemplate.execute(script,
                Arrays.asList(stockKey(seckillProduct.getId()), orderKey(seckillProduct.getId(), userId),
                        limitKey(seckillProduct.getId(), userId)),
                safePurchaseLimit(seckillProduct));
        return PreDeductResult.fromCode(result);
    }

    public void ensureStockLoaded(SeckillProduct seckillProduct) {
        String stockKey = stockKey(seckillProduct.getId());
        if (Boolean.FALSE.equals(redisTemplate.hasKey(stockKey))) {
            redisTemplate.opsForValue().set(stockKey, seckillProduct.getSeckillStock());
        }
    }

    public void markOrderProcessing(Long orderId) {
        updateOrderStatus(orderId, STATUS_PROCESSING);
    }

    public void markOrderCreated(Long userId, Long seckillId, Long orderId) {
        redisTemplate.opsForValue().set(orderKey(seckillId, userId), orderId, 24, TimeUnit.HOURS);
        updateOrderStatus(orderId, SeckillOrderStatus.PENDING_PAYMENT.getRedisStatus());
    }

    public void markOrderPaid(Long orderId) {
        updateOrderStatus(orderId, SeckillOrderStatus.PAID.getRedisStatus());
    }

    public void markOrderFailed(Long orderId) {
        updateOrderStatus(orderId, STATUS_FAILED);
    }

    public void releaseReservation(Long userId, Long seckillId, Long orderId) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(RELEASE_SCRIPT, Long.class);
        redisTemplate.execute(script, Arrays.asList(
                stockKey(seckillId),
                orderKey(seckillId, userId),
                limitKey(seckillId, userId)));
        markOrderFailed(orderId);
    }

    public Object getOrderStatus(Long orderId) {
        return redisTemplate.opsForValue().get(orderStatusKey(orderId));
    }

    public void syncOrderStatus(SeckillOrder order) {
        SeckillOrderStatus status = SeckillOrderStatus.fromCode(order.getStatus());
        updateOrderStatus(order.getId(), status != null ? status.getRedisStatus() : STATUS_UNKNOWN);
    }

    public String orderStatusKey(Long orderId) {
        return SECKILL_ORDER_STATUS_KEY + orderId;
    }

    private void updateOrderStatus(Long orderId, String status) {
        redisTemplate.opsForValue().set(orderStatusKey(orderId), status, 1, TimeUnit.HOURS);
    }

    private Integer safePurchaseLimit(SeckillProduct seckillProduct) {
        Integer purchaseLimit = seckillProduct.getPurchaseLimit();
        return purchaseLimit == null || purchaseLimit < 1 ? 1 : purchaseLimit;
    }

    private String stockKey(Long seckillId) {
        return SECKILL_STOCK_KEY + seckillId;
    }

    private String orderKey(Long seckillId, Long userId) {
        return SECKILL_ORDER_KEY + seckillId + ":" + userId;
    }

    private String limitKey(Long seckillId, Long userId) {
        return SECKILL_LIMIT_KEY + seckillId + ":" + userId;
    }

    public enum PreDeductResult {
        SUCCESS,
        DUPLICATE,
        NO_STOCK,
        LIMIT_EXCEEDED,
        SYSTEM_ERROR;

        public static PreDeductResult fromCode(Long code) {
            if (code == null) {
                return SYSTEM_ERROR;
            }
            if (code == 0L) {
                return SUCCESS;
            }
            if (code == 1L) {
                return DUPLICATE;
            }
            if (code == 2L) {
                return NO_STOCK;
            }
            if (code == 3L) {
                return LIMIT_EXCEEDED;
            }
            return SYSTEM_ERROR;
        }
    }
}
