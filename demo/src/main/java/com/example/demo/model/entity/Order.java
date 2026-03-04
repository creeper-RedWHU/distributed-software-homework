package com.example.demo.model.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class Order {
    private Long id;
    private String orderNo;
    private Long userId;
    private Long activityId;
    private Long productId;
    private Integer quantity;
    private BigDecimal totalAmount;
    private Integer status;
    private LocalDateTime payTime;
    private LocalDateTime expireTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
