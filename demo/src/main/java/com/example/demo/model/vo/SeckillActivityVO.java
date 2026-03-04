package com.example.demo.model.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SeckillActivityVO {
    private Long activityId;
    private String name;
    private String productName;
    private BigDecimal originalPrice;
    private BigDecimal seckillPrice;
    private String imageUrl;
    private Integer availableStock;
    private Integer totalStock;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer status;
    private Integer limitPerUser;
    private LocalDateTime serverTime;
}
