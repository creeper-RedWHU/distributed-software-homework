package com.example.demo.common;

import lombok.Getter;

/**
 * 错误码枚举
 */
@Getter
public enum ErrorCode {

    // 通用
    SUCCESS(200, "success"),
    PARAM_ERROR(400, "参数错误"),
    BAD_REQUEST(400, "参数错误"),
    NOT_LOGIN(401, "未登录"),
    UNAUTHORIZED(401, "未登录"),
    FORBIDDEN(403, "无权限"),
    SYSTEM_ERROR(5000, "系统错误"),

    // 用户相关 4xxx
    USER_NOT_FOUND(4001, "用户不存在"),
    USER_ALREADY_EXISTS(4002, "用户名已存在"),
    PHONE_ALREADY_EXISTS(4003, "手机号已注册"),
    EMAIL_ALREADY_EXISTS(4004, "邮箱已注册"),
    PASSWORD_ERROR(4005, "密码错误"),
    USER_DISABLED(4006, "用户已被禁用"),
    USER_LOCKED(4007, "用户已被锁定"),
    TOKEN_INVALID(4008, "Token 无效"),
    TOKEN_EXPIRED(4009, "Token 已过期");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
