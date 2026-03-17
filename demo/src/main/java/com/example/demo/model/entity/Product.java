package com.example.demo.model.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class Product {
    private Long id;
    private String productName;
    private String description;
    private String imageUrl;
    private BigDecimal price;
    private Integer stock;
    private Integer status; // 0下架 1上架
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
