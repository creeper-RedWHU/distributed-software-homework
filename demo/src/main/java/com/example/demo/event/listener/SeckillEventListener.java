package com.example.demo.event.listener;

import com.example.demo.event.*;
import com.example.demo.websocket.WebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 秒杀事件监听器 — 处理秒杀结果推送
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillEventListener {

    private final WebSocketService webSocketService;

    @Async
    @EventListener
    public void onSeckillSuccess(SeckillSuccessEvent event) {
        log.info("秒杀成功事件: userId={}, orderNo={}", event.getUserId(), event.getOrderNo());
        webSocketService.sendToUser(event.getUserId(), Map.of(
                "type", "SECKILL_SUCCESS",
                "orderNo", event.getOrderNo(),
                "activityId", event.getActivityId()
        ));
    }

    @Async
    @EventListener
    public void onSeckillFailed(SeckillFailedEvent event) {
        log.info("秒杀失败事件: userId={}, reason={}", event.getUserId(), event.getReason());
        webSocketService.sendToUser(event.getUserId(), Map.of(
                "type", "SECKILL_FAILED",
                "reason", event.getReason()
        ));
    }

    @Async
    @EventListener
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("订单创建事件: orderNo={}, userId={}", event.getOrderNo(), event.getUserId());
        webSocketService.sendToUser(event.getUserId(), Map.of(
                "type", "ORDER_CREATED",
                "orderNo", event.getOrderNo(),
                "totalAmount", event.getTotalAmount()
        ));
    }

    @Async
    @EventListener
    public void onStockEmpty(StockEmptyEvent event) {
        log.info("库存售罄事件: activityId={}", event.getActivityId());
        webSocketService.broadcastToActivity(event.getActivityId(), Map.of(
                "type", "STOCK_EMPTY",
                "activityId", event.getActivityId()
        ));
    }

    @Async
    @EventListener
    public void onOrderTimeout(OrderTimeoutEvent event) {
        log.info("订单超时事件: orderNo={}", event.getOrderNo());
        webSocketService.sendToUser(event.getUserId(), Map.of(
                "type", "ORDER_TIMEOUT",
                "orderNo", event.getOrderNo()
        ));
    }
}
