package com.example.demo.config;

import com.example.demo.common.Constants;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic seckillOrdersTopic() {
        return TopicBuilder.name(Constants.TOPIC_SECKILL_ORDERS)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic stockSyncTopic() {
        return TopicBuilder.name(Constants.TOPIC_STOCK_SYNC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic eventNotificationTopic() {
        return TopicBuilder.name(Constants.TOPIC_EVENT_NOTIFICATION)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            KafkaTemplate<String, Object> kafkaTemplate) {

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        // 重试 3 次，间隔 1 秒，失败后发送到死信队列
        factory.setCommonErrorHandler(new DefaultErrorHandler(
                new DeadLetterPublishingRecoverer(kafkaTemplate),
                new FixedBackOff(1000L, 3L)
        ));

        return factory;
    }
}
