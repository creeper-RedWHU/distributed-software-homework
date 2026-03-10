package com.example.demo.model.dto;

import lombok.Data;
import javax.validation.constraints.Email;
import javax.validation.constraints.Pattern;
import java.time.LocalDate;

/**
 * 用户信息更新请求 DTO
 */
@Data
public class UserUpdateRequest {

    private String nickname;

    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @Email(message = "邮箱格式不正确")
    private String email;

    private String avatarUrl;

    private Integer gender; // 0未知 1男 2女

    private LocalDate birthday;
}
