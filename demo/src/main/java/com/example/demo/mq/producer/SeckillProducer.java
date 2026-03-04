package com.example.demo.mq.producer;

import com.example.demo.common.Constants;
import com.example.demo.model.dto.SeckillOrderMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 秒杀消息生产者
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * 发送秒杀订单消息到 Kafka
     * Key 使用 activityId 保证同一活动的消息落到同一分区（保序）
     */
    public void sendSeckillOrder(SeckillOrderMessage message) {
        kafkaTemplate.send(Constants.TOPIC_SECKILL_ORDERS,
                        String.valueOf(message.getActivityId()), message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Kafka 消息发送失败: orderNo={}", message.getOrderNo(), ex);
                    } else {
                        log.debug("Kafka 消息发送成功: orderNo={}, partition={}",
                                message.getOrderNo(), result.getRecordMetadata().partition());
                    }
                });
    }
}
