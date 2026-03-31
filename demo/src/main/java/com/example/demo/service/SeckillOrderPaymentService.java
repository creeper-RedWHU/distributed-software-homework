package com.example.demo.service;

import com.example.demo.mapper.SeckillOrderMapper;
import com.example.demo.model.dto.SeckillOrderPayMessage;
import com.example.demo.model.entity.SeckillOrder;
import com.example.demo.model.enums.SeckillOrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillOrderPaymentService {

    private final SeckillOrderMapper seckillOrderMapper;
    private final SeckillRedisService seckillRedisService;

    @Transactional(rollbackFor = Exception.class)
    public boolean processPayOrder(SeckillOrderPayMessage message) {
        SeckillOrder order = seckillOrderMapper.selectById(message.getOrderId());
        if (order == null) {
            log.warn("支付消息对应订单不存在: orderId={}", message.getOrderId());
            seckillRedisService.markOrderFailed(message.getOrderId());
            return false;
        }
        if (!order.getUserId().equals(message.getUserId())) {
            log.warn("支付用户与订单不匹配: orderId={}, requestUserId={}, orderUserId={}",
                    message.getOrderId(), message.getUserId(), order.getUserId());
            seckillRedisService.markOrderFailed(message.getOrderId());
            return false;
        }
        if (SeckillOrderStatus.PAID.getCode() == order.getStatus()) {
            seckillRedisService.markOrderPaid(order.getId());
            return true;
        }
        if (SeckillOrderStatus.PENDING_PAYMENT.getCode() != order.getStatus()) {
            log.warn("订单当前状态不允许支付: orderId={}, status={}", order.getId(), order.getStatus());
            seckillRedisService.syncOrderStatus(order);
            return false;
        }

        int updated = seckillOrderMapper.updateStatus(
                order.getId(),
                SeckillOrderStatus.PENDING_PAYMENT.getCode(),
                SeckillOrderStatus.PAID.getCode());
        if (updated == 0) {
            SeckillOrder latest = seckillOrderMapper.selectById(order.getId());
            if (latest != null) {
                seckillRedisService.syncOrderStatus(latest);
                return SeckillOrderStatus.PAID.getCode() == latest.getStatus();
            }
            seckillRedisService.markOrderFailed(order.getId());
            return false;
        }

        seckillRedisService.markOrderPaid(order.getId());
        log.info("订单支付完成: orderId={}, payTime={}", order.getId(), message.getPayTime());
        return true;
    }
}
