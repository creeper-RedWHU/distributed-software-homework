package com.example.demo.service;

import com.example.demo.mapper.SeckillOrderMapper;
import com.example.demo.model.dto.SeckillOrderPayMessage;
import com.example.demo.model.entity.SeckillOrder;
import com.example.demo.model.enums.SeckillOrderStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeckillOrderPaymentServiceTest {

    @Mock
    private SeckillOrderMapper seckillOrderMapper;
    @Mock
    private SeckillRedisService seckillRedisService;

    @InjectMocks
    private SeckillOrderPaymentService seckillOrderPaymentService;

    @Test
    void shouldFailWhenOrderDoesNotExist() {
        SeckillOrderPayMessage message = new SeckillOrderPayMessage(10L, 2L, LocalDateTime.now());
        when(seckillOrderMapper.selectById(10L)).thenReturn(null);

        boolean success = seckillOrderPaymentService.processPayOrder(message);

        assertFalse(success);
        verify(seckillRedisService).markOrderFailed(10L);
    }

    @Test
    void shouldUpdateOrderStatusToPaid() {
        SeckillOrder order = buildPendingOrder();
        when(seckillOrderMapper.selectById(10L)).thenReturn(order);
        when(seckillOrderMapper.updateStatus(10L, 0, 1)).thenReturn(1);

        boolean success = seckillOrderPaymentService.processPayOrder(
                new SeckillOrderPayMessage(10L, 2L, LocalDateTime.now()));

        assertTrue(success);
        verify(seckillRedisService).markOrderPaid(10L);
    }

    @Test
    void shouldReturnTrueWhenOrderAlreadyPaid() {
        SeckillOrder order = buildPendingOrder();
        order.setStatus(SeckillOrderStatus.PAID.getCode());
        when(seckillOrderMapper.selectById(10L)).thenReturn(order);

        boolean success = seckillOrderPaymentService.processPayOrder(
                new SeckillOrderPayMessage(10L, 2L, LocalDateTime.now()));

        assertTrue(success);
        verify(seckillRedisService).markOrderPaid(10L);
    }

    @Test
    void shouldTreatConcurrentPaidUpdateAsSuccess() {
        SeckillOrder order = buildPendingOrder();
        SeckillOrder latest = buildPendingOrder();
        latest.setStatus(SeckillOrderStatus.PAID.getCode());
        when(seckillOrderMapper.selectById(10L)).thenReturn(order, latest);
        when(seckillOrderMapper.updateStatus(10L, 0, 1)).thenReturn(0);

        boolean success = seckillOrderPaymentService.processPayOrder(
                new SeckillOrderPayMessage(10L, 2L, LocalDateTime.now()));

        assertTrue(success);
        verify(seckillRedisService).syncOrderStatus(latest);
    }

    private SeckillOrder buildPendingOrder() {
        SeckillOrder order = new SeckillOrder();
        order.setId(10L);
        order.setUserId(2L);
        order.setStatus(SeckillOrderStatus.PENDING_PAYMENT.getCode());
        return order;
    }
}
