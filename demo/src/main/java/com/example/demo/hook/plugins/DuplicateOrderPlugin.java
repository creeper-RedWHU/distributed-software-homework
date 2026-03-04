package com.example.demo.hook.plugins;

import com.example.demo.hook.*;
import com.example.demo.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 防重复下单插件 — 在秒杀执行前检查用户是否已下单
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DuplicateOrderPlugin implements HookPlugin {

    private final OrderMapper orderMapper;

    @Override
    public Set<HookPoint> bindPoints() {
        return Set.of(HookPoint.BEFORE_SECKILL_EXECUTE);
    }

    @Override
    public HookResult execute(HookPoint point, HookContext context) {
        Long userId = context.get("userId", Long.class);
        Long activityId = context.get("activityId", Long.class);
        if (userId == null || activityId == null) return HookResult.ok();

        int count = orderMapper.countByUserAndActivity(userId, activityId);
        if (count > 0) {
            log.warn("用户重复下单: userId={}, activityId={}", userId, activityId);
            return HookResult.abort("您已参与此活动，请勿重复下单");
        }
        return HookResult.ok();
    }

    @Override
    public int order() {
        return 10;
    }
}
