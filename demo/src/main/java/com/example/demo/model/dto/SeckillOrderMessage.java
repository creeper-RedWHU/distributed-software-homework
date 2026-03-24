package com.example.demo.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 秒杀订单 Kafka 消息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeckillOrderMessage {
    private Long orderId;
    private Long userId;
    private Long seckillId;
    private Long productId;
    private BigDecimal orderPrice;
}
