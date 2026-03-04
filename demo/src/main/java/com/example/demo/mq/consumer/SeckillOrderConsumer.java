package com.example.demo.mq.consumer;

import com.example.demo.common.Constants;
import com.example.demo.event.*;
import com.example.demo.model.dto.SeckillOrderMessage;
import com.example.demo.model.entity.Order;
import com.example.demo.service.OrderService;
import com.example.demo.service.SeckillService;
import com.example.demo.service.StockService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * 秒杀订单消费者 — 异步创建订单
 *
 * 核心流程:
 * 1. 消费 Kafka 消息
 * 2. MySQL 乐观锁扣减库存（兜底）
 * 3. 创建订单记录
 * 4. 发布事件（WebSocket 通知用户）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillOrderConsumer {

    private final OrderService orderService;
    private final StockService stockService;
    private final SeckillService seckillService;
    private final EventBus eventBus;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = Constants.TOPIC_SECKILL_ORDERS, groupId = "order-consumer-group")
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        SeckillOrderMessage message = null;
        try {
            message = objectMapper.readValue(record.value(), SeckillOrderMessage.class);
            log.info("消费秒杀订单消息: orderNo={}, userId={}", message.getOrderNo(), message.getUserId());

            // 1. MySQL 乐观锁扣减（Redis 的兜底确认）
            boolean deducted = stockService.deductStockMySQL(message.getActivityId(), message.getQuantity());
            if (!deducted) {
                // MySQL 扣减失败 → Redis 回滚
                stockService.rollbackStockRedis(message.getActivityId(), message.getUserId(), message.getQuantity());
                seckillService.clearSoldOutMark(message.getActivityId());

                eventBus.publish(new SeckillFailedEvent(this,
                        message.getActivityId(), message.getUserId(),
                        message.getOrderNo(), "库存扣减失败"));
                ack.acknowledge();
                return;
            }

            // 2. 创建订单
            Order order = orderService.createOrder(message);
            if (order == null) {
                // 幂等：订单已存在
                ack.acknowledge();
                return;
            }

            // 3. 记录库存流水
            stockService.logStockChange(message.getActivityId(), message.getOrderNo(),
                    message.getUserId(), message.getQuantity(), Constants.STOCK_LOG_DEDUCT);

            // 4. 发布成功事件
            eventBus.publish(new OrderCreatedEvent(this,
                    message.getOrderNo(), message.getUserId(),
                    message.getActivityId(), message.getTotalAmount()));

            eventBus.publish(new SeckillSuccessEvent(this,
                    message.getActivityId(), message.getUserId(), message.getOrderNo()));

            ack.acknowledge();
            log.info("订单处理完成: orderNo={}", message.getOrderNo());

        } catch (Exception e) {
            log.error("订单处理异常: orderNo={}",
                    message != null ? message.getOrderNo() : "unknown", e);
            // 不 ack，触发 Kafka 重试
        }
    }
}
