package com.example.demo.kafka;

import com.example.demo.config.KafkaConfig;
import com.example.demo.model.dto.SeckillOrderMessage;
import com.example.demo.service.SeckillOrderProcessService;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 秒杀订单消费者 - 异步处理订单创建（削峰填谷）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillOrderConsumer {

    private final SeckillOrderProcessService seckillOrderProcessService;

    @KafkaListener(topics = KafkaConfig.SECKILL_ORDER_TOPIC, groupId = "seckill-order-group")
    public void consumeSeckillOrder(SeckillOrderMessage message) {
        seckillOrderProcessService.processCreateOrder(message);
    }
}
