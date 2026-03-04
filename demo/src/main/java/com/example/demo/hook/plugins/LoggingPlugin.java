package com.example.demo.hook.plugins;

import com.example.demo.hook.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 日志插件 — 在秒杀执行后记录操作日志
 */
@Slf4j
@Component
public class LoggingPlugin implements HookPlugin {

    @Override
    public Set<HookPoint> bindPoints() {
        return Set.of(
                HookPoint.AFTER_SECKILL_EXECUTE,
                HookPoint.AFTER_ORDER_CREATE,
                HookPoint.AFTER_ORDER_PAY,
                HookPoint.AFTER_ORDER_CANCEL
        );
    }

    @Override
    public HookResult execute(HookPoint point, HookContext context) {
        log.info("[Hook日志] point={}, context={}", point, context.toMap());
        return HookResult.ok();
    }

    @Override
    public int order() {
        return 999; // 最低优先级，最后执行
    }
}
