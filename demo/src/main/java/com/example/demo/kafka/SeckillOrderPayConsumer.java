package com.example.demo.kafka;

import com.example.demo.config.KafkaConfig;
import com.example.demo.model.dto.SeckillOrderPayMessage;
import com.example.demo.service.SeckillOrderPaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SeckillOrderPayConsumer {

    private final SeckillOrderPaymentService seckillOrderPaymentService;

    @KafkaListener(topics = KafkaConfig.SECKILL_ORDER_PAY_TOPIC, groupId = "seckill-order-pay-group")
    public void consumePayOrder(SeckillOrderPayMessage message) {
        seckillOrderPaymentService.processPayOrder(message);
    }
}
