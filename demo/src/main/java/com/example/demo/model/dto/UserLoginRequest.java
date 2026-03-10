package com.example.demo.model.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;

/**
 * 用户登录请求 DTO
 */
@Data
public class UserLoginRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;
}
