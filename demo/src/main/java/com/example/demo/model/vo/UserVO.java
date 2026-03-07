package com.example.demo.model.vo;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户信息视图对象 VO
 * 返回给前端，不包含敏感信息（如密码）
 */
@Data
public class UserVO {

    private Long id;

    private String username;

    private String nickname;

    private String phone;

    private String email;

    private String avatarUrl;

    private Integer gender; // 0未知 1男 2女

    private LocalDate birthday;

    private Integer status; // 0禁用 1正常 2锁定

    private LocalDateTime lastLoginTime;

    private LocalDateTime createdAt;
}
