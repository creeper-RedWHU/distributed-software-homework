package com.example.demo.model.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SeckillProductVO {
    private Long id;
    private Long productId;
    private String productName;
    private String description;
    private String imageUrl;
    private BigDecimal originalPrice;
    private BigDecimal seckillPrice;
    private Integer seckillStock;
    private Integer purchaseLimit;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer status;
}
