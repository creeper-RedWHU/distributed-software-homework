package com.example.demo.kafka;

import com.example.demo.config.KafkaConfig;
import com.example.demo.model.dto.SeckillOrderMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.springframework.util.concurrent.ListenableFutureCallback;

@Slf4j
@Component
public class SeckillOrderProducer {

    @Autowired
    private KafkaTemplate<String, SeckillOrderMessage> kafkaTemplate;

    public void sendSeckillOrder(SeckillOrderMessage message) {
        String key = String.valueOf(message.getUserId());
        kafkaTemplate.send(KafkaConfig.SECKILL_ORDER_TOPIC, key, message)
                .addCallback(new ListenableFutureCallback<SendResult<String, SeckillOrderMessage>>() {
                    @Override
                    public void onSuccess(SendResult<String, SeckillOrderMessage> result) {
                        log.info("秒杀订单消息发送成功: orderId={}, partition={}",
                                message.getOrderId(), result.getRecordMetadata().partition());
                    }

                    @Override
                    public void onFailure(Throwable ex) {
                        log.error("秒杀订单消息发送失败: orderId={}, error={}",
                                message.getOrderId(), ex.getMessage());
                    }
                });
    }
}
