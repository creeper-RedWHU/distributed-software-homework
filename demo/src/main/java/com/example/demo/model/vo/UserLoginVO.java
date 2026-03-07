package com.example.demo.model.vo;

import lombok.Data;

/**
 * 用户登录成功返回 VO
 */
@Data
public class UserLoginVO {

    private Long userId;

    private String username;

    private String token; // JWT Token

    private Long expiresIn; // 过期时间（秒）
}
