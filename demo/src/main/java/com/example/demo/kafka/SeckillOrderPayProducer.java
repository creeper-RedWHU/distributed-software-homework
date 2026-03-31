package com.example.demo.kafka;

import com.example.demo.config.KafkaConfig;
import com.example.demo.model.dto.SeckillOrderPayMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillOrderPayProducer {

    private final KafkaTemplate<String, SeckillOrderPayMessage> kafkaTemplate;

    public boolean sendPayOrderMessage(SeckillOrderPayMessage message) {
        try {
            kafkaTemplate.send(KafkaConfig.SECKILL_ORDER_PAY_TOPIC, String.valueOf(message.getUserId()), message)
                    .get(5, TimeUnit.SECONDS);
            log.info("订单支付消息发送成功: orderId={}", message.getOrderId());
            return true;
        } catch (Exception ex) {
            log.error("订单支付消息发送失败: orderId={}, error={}", message.getOrderId(), ex.getMessage(), ex);
            return false;
        }
    }
}
