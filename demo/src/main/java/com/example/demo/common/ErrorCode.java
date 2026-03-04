package com.example.demo.common;

import lombok.Getter;

/**
 * 错误码枚举
 */
@Getter
public enum ErrorCode {

    // 通用
    SUCCESS(200, "success"),
    BAD_REQUEST(400, "参数错误"),
    UNAUTHORIZED(401, "未登录"),
    FORBIDDEN(403, "无权限"),
    SYSTEM_ERROR(5000, "系统错误"),

    // 秒杀相关 1xxx
    ACTIVITY_NOT_FOUND(1001, "活动不存在"),
    ACTIVITY_NOT_STARTED(1002, "活动未开始"),
    ACTIVITY_ENDED(1003, "活动已结束"),
    STOCK_EMPTY(1004, "库存不足，秒杀失败"),
    PURCHASE_LIMIT_EXCEEDED(1005, "超出限购数量"),
    DUPLICATE_ORDER(1006, "重复下单"),
    RATE_LIMITED(1007, "请求过于频繁，请稍后再试"),
    SECKILL_HOOK_BLOCKED(1008, "请求被拦截"),

    // 订单相关 2xxx
    ORDER_NOT_FOUND(2001, "订单不存在"),
    ORDER_STATUS_ERROR(2002, "订单状态异常"),
    ORDER_EXPIRED(2003, "订单已过期"),
    ORDER_ALREADY_PAID(2004, "订单已支付"),

    // 商品相关 3xxx
    PRODUCT_NOT_FOUND(3001, "商品不存在"),
    PRODUCT_OFF_SHELF(3002, "商品已下架");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
