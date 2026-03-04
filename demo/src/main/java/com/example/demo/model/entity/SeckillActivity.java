package com.example.demo.model.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SeckillActivity {
    private Long id;
    private String name;
    private Long productId;
    private BigDecimal seckillPrice;
    private Integer totalStock;
    private Integer availableStock;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer status;
    private Integer limitPerUser;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
