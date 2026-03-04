package com.example.demo.hook.plugins;

import com.example.demo.hook.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * 限流插件 — 在秒杀执行前检查用户请求频率
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitPlugin implements HookPlugin {

    private final RedisTemplate<String, Object> redisTemplate;
    private final DefaultRedisScript<Long> rateLimitScript;

    @Value("${seckill.rate-limit.max-requests:5}")
    private int maxRequests;

    @Value("${seckill.rate-limit.window-seconds:1}")
    private int windowSeconds;

    @Override
    public Set<HookPoint> bindPoints() {
        return Set.of(HookPoint.BEFORE_SECKILL_EXECUTE);
    }

    @Override
    public HookResult execute(HookPoint point, HookContext context) {
        Long userId = context.get("userId", Long.class);
        if (userId == null) return HookResult.ok();

        String key = "rate:limit:" + userId;
        long now = System.currentTimeMillis();
        long windowStart = now - windowSeconds * 1000L;

        Long result = redisTemplate.execute(rateLimitScript,
                List.of(key),
                windowStart, maxRequests, now, windowSeconds + 1);

        if (Long.valueOf(1).equals(result)) {
            log.warn("用户请求过于频繁: userId={}", userId);
            return HookResult.abort("请求过于频繁，请稍后再试");
        }
        return HookResult.ok();
    }

    @Override
    public int order() {
        return 1; // 最高优先级
    }
}
