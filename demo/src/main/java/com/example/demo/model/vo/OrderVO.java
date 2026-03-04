package com.example.demo.model.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OrderVO {
    private String orderNo;
    private String productName;
    private BigDecimal seckillPrice;
    private Integer quantity;
    private BigDecimal totalAmount;
    private Integer status;
    private String statusText;
    private LocalDateTime expireTime;
    private LocalDateTime createdAt;
}
