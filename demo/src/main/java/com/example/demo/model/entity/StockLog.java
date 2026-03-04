package com.example.demo.model.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class StockLog {
    private Long id;
    private Long activityId;
    private String orderNo;
    private Long userId;
    private Integer quantity;
    private Integer type;
    private LocalDateTime createdAt;
}
