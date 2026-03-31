package com.example.demo.kafka;

import com.example.demo.config.KafkaConfig;
import com.example.demo.model.dto.SeckillOrderMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillOrderProducer {

    private final KafkaTemplate<String, SeckillOrderMessage> kafkaTemplate;

    public boolean sendSeckillOrder(SeckillOrderMessage message) {
        String key = String.valueOf(message.getUserId());
        try {
            kafkaTemplate.send(KafkaConfig.SECKILL_ORDER_TOPIC, key, message).get(5, TimeUnit.SECONDS);
            log.info("秒杀订单消息发送成功: orderId={}", message.getOrderId());
            return true;
        } catch (Exception ex) {
            log.error("秒杀订单消息发送失败: orderId={}, error={}",
                    message.getOrderId(), ex.getMessage(), ex);
            return false;
        }
    }
}
