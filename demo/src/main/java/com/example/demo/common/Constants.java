package com.example.demo.common;

/**
 * 系统常量
 */
public final class Constants {

    private Constants() {}

    // ===== Redis Key 前缀 =====
    public static final String REDIS_STOCK_KEY = "seckill:stock:";
    public static final String REDIS_BOUGHT_KEY = "seckill:bought:";
    public static final String REDIS_LOCK_KEY = "seckill:lock:";
    public static final String REDIS_ACTIVITY_KEY = "seckill:activity:";
    public static final String REDIS_PRODUCT_KEY = "product:detail:";
    public static final String REDIS_RATE_LIMIT_KEY = "rate:limit:";
    public static final String REDIS_SOLD_OUT_KEY = "seckill:soldout:";

    // ===== Kafka Topic =====
    public static final String TOPIC_SECKILL_ORDERS = "seckill-orders";
    public static final String TOPIC_STOCK_SYNC = "stock-sync";
    public static final String TOPIC_EVENT_NOTIFICATION = "event-notification";

    // ===== 订单状态 =====
    public static final int ORDER_STATUS_UNPAID = 0;
    public static final int ORDER_STATUS_PAID = 1;
    public static final int ORDER_STATUS_CANCELLED = 2;
    public static final int ORDER_STATUS_REFUNDED = 3;
    public static final int ORDER_STATUS_TIMEOUT = 4;

    // ===== 活动状态 =====
    public static final int ACTIVITY_NOT_STARTED = 0;
    public static final int ACTIVITY_IN_PROGRESS = 1;
    public static final int ACTIVITY_ENDED = 2;
    public static final int ACTIVITY_CANCELLED = 3;

    // ===== 库存流水类型 =====
    public static final int STOCK_LOG_DEDUCT = 1;
    public static final int STOCK_LOG_ROLLBACK = 2;
    public static final int STOCK_LOG_MANUAL = 3;

    // ===== 事件日志状态 =====
    public static final int EVENT_PENDING = 0;
    public static final int EVENT_PROCESSED = 1;
    public static final int EVENT_FAILED = 2;
}
