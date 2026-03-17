package com.example.demo.model.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SeckillOrder {
    private Long id;
    private Long userId;
    private Long seckillId;
    private Long productId;
    private BigDecimal orderPrice;
    private Integer status; // 0未支付 1已支付 2已取消
    private LocalDateTime createdAt;
}
