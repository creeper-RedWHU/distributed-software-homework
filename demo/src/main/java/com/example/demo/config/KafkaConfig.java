package com.example.demo.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    public static final String SECKILL_ORDER_TOPIC = "seckill-order";
    public static final String SECKILL_ORDER_PAY_TOPIC = "seckill-order-pay";

    @Bean
    public NewTopic seckillOrderTopic() {
        return TopicBuilder.name(SECKILL_ORDER_TOPIC)
                .partitions(4)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic seckillOrderPayTopic() {
        return TopicBuilder.name(SECKILL_ORDER_PAY_TOPIC)
                .partitions(4)
                .replicas(1)
                .build();
    }
}
