package com.example.demo.model.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SeckillProduct {
    private Long id;
    private Long productId;
    private BigDecimal seckillPrice;
    private Integer seckillStock;
    private Integer purchaseLimit;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer status; // 0未开始 1进行中 2已结束
    private LocalDateTime createdAt;
}
