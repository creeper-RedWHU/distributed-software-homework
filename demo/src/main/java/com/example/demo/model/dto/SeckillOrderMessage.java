package com.example.demo.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Kafka 秒杀订单消息体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeckillOrderMessage {
    private String orderNo;
    private Long userId;
    private Long activityId;
    private Long productId;
    private Integer quantity;
    private BigDecimal totalAmount;
    private LocalDateTime createTime;
}
