package com.example.demo.hook;

/**
 * Hook 切入点枚举 — 定义系统中所有可插拔的位置
 */
public enum HookPoint {
    // 秒杀
    BEFORE_SECKILL_EXECUTE,
    AFTER_SECKILL_EXECUTE,

    // 订单
    BEFORE_ORDER_CREATE,
    AFTER_ORDER_CREATE,
    BEFORE_ORDER_PAY,
    AFTER_ORDER_PAY,
    BEFORE_ORDER_CANCEL,
    AFTER_ORDER_CANCEL,

    // 库存
    ON_STOCK_WARMUP,
    ON_STOCK_EMPTY,
    ON_STOCK_ROLLBACK,

    // 活动
    BEFORE_ACTIVITY_CREATE,
    ON_ACTIVITY_START,
    ON_ACTIVITY_END,
}
