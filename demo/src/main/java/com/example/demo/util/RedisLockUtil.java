package com.example.demo.util;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Redis 分布式锁工具
 */
@Component
@RequiredArgsConstructor
public class RedisLockUtil {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String UNLOCK_SCRIPT =
            "if redis.call('GET', KEYS[1]) == ARGV[1] then " +
            "  return redis.call('DEL', KEYS[1]) " +
            "else return 0 end";

    public String tryLock(String lockKey, long expireMs) {
        String requestId = UUID.randomUUID().toString();
        Boolean result = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, requestId, expireMs, TimeUnit.MILLISECONDS);
        return Boolean.TRUE.equals(result) ? requestId : null;
    }

    public boolean unlock(String lockKey, String requestId) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class);
        Long result = redisTemplate.execute(script, List.of(lockKey), requestId);
        return Long.valueOf(1).equals(result);
    }
}
