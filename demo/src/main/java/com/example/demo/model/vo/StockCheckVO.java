package com.example.demo.model.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class StockCheckVO {
    private Long activityId;
    private Integer redisStock;
    private Integer mysqlStock;
    private Boolean consistent;
    private Integer soldCount;
    private Integer orderCount;
    private LocalDateTime checkedAt;
}
