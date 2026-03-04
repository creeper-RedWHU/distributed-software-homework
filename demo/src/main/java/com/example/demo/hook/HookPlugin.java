package com.example.demo.hook;

import java.util.Set;

/**
 * Hook 插件接口 — 所有自定义插件实现此接口
 */
public interface HookPlugin {

    /** 此插件关注哪些切入点 */
    Set<HookPoint> bindPoints();

    /** 执行 Hook 逻辑 */
    HookResult execute(HookPoint point, HookContext context);

    /** 优先级（数字越小优先级越高） */
    default int order() {
        return 100;
    }
}
