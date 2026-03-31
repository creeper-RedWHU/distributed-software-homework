package com.example.demo.model.enums;

import lombok.Getter;

@Getter
public enum SeckillOrderStatus {

    PENDING_PAYMENT(0, "PENDING_PAYMENT"),
    PAID(1, "PAID"),
    CANCELLED(2, "CANCELLED"),
    CREATE_FAILED(3, "CREATE_FAILED");

    private final int code;
    private final String redisStatus;

    SeckillOrderStatus(int code, String redisStatus) {
        this.code = code;
        this.redisStatus = redisStatus;
    }

    public static SeckillOrderStatus fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (SeckillOrderStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }
}
