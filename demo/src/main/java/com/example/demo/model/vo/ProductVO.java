package com.example.demo.model.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ProductVO {
    private Long id;
    private String productName;
    private String description;
    private String imageUrl;
    private BigDecimal price;
    private Integer stock;
    private Integer status;
    private LocalDateTime createdAt;
}
