package com.example.demo.model.enums;

import lombok.Getter;

/**
 * 用户类型枚举
 */
@Getter
public enum UserTypeEnum {
    /**
     * 商家
     */
    MERCHANT("MERCHANT", "商家"),

    /**
     * 买家
     */
    BUYER("BUYER", "买家"),

    /**
     * 管理者
     */
    ADMIN("ADMIN", "管理者");

    private final String code;
    private final String desc;

    UserTypeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据代码获取枚举
     */
    public static UserTypeEnum fromCode(String code) {
        for (UserTypeEnum type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
