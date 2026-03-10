package com.example.demo.model.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户登录实体类
 * 对应数据库表 t_user_login
 */
@Data
public class UserLogin {
    /**
     * 主键ID
     */
    private Long id;

    /**
     * 用户ID（关联t_user表）
     */
    private Long userId;

    /**
     * 登录用户名
     */
    private String username;

    /**
     * 登录密码（加密后）
     */
    private String password;

    /**
     * 用户类型: MERCHANT(商家)/BUYER(买家)/ADMIN(管理者)
     */
    private String userType;

    /**
     * 密码盐值
     */
    private String salt;

    /**
     * 状态: 0禁用 1正常
     */
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
