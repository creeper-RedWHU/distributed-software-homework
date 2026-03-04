package com.example.demo.model.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class User {
    private Long id;
    private String username;
    private String passwordHash;
    private String phone;
    private String email;
    private Integer status;
    private LocalDateTime createdAt;
}
