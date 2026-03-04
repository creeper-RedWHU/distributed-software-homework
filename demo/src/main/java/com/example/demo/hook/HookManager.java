package com.example.demo.hook;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Hook 管理器 — 收集所有插件，在切入点执行 Hook 链
 */
@Slf4j
@Component
public class HookManager {

    private final Map<HookPoint, List<HookPlugin>> registry = new EnumMap<>(HookPoint.class);

    @Autowired
    public HookManager(List<HookPlugin> plugins) {
        for (HookPlugin plugin : plugins) {
            for (HookPoint point : plugin.bindPoints()) {
                registry.computeIfAbsent(point, k -> new ArrayList<>()).add(plugin);
            }
        }
        registry.values().forEach(list ->
                list.sort(Comparator.comparingInt(HookPlugin::order)));

        log.info("Hook 管理器初始化完成: {} 个插件, {} 个切入点",
                plugins.size(), registry.size());
    }

    /**
     * 执行某个切入点的所有 Hook
     */
    public HookResult executeHooks(HookPoint point, HookContext context) {
        List<HookPlugin> hooks = registry.getOrDefault(point, List.of());
        for (HookPlugin hook : hooks) {
            try {
                HookResult result = hook.execute(point, context);
                if (!result.isProceed()) {
                    log.warn("Hook 阻止操作: point={}, plugin={}, reason={}",
                            point, hook.getClass().getSimpleName(), result.getMessage());
                    return result;
                }
                if (result.getData() != null) {
                    context.putAll(result.getData());
                }
            } catch (Exception e) {
                log.error("Hook 执行异常: point={}, plugin={}",
                        point, hook.getClass().getSimpleName(), e);
            }
        }
        return HookResult.ok();
    }
}
