package com.example.demo.service;

import com.example.demo.common.Result;
import com.example.demo.kafka.SeckillOrderPayProducer;
import com.example.demo.kafka.SeckillOrderProducer;
import com.example.demo.mapper.ProductMapper;
import com.example.demo.mapper.SeckillOrderMapper;
import com.example.demo.mapper.SeckillProductMapper;
import com.example.demo.model.entity.Product;
import com.example.demo.model.entity.SeckillOrder;
import com.example.demo.model.entity.SeckillProduct;
import com.example.demo.model.enums.SeckillOrderStatus;
import com.example.demo.util.SnowflakeIdGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeckillServiceTest {

    @Mock
    private SeckillProductMapper seckillProductMapper;
    @Mock
    private SeckillOrderMapper seckillOrderMapper;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private SeckillOrderProducer seckillOrderProducer;
    @Mock
    private SeckillOrderPayProducer seckillOrderPayProducer;
    @Mock
    private SeckillRedisService seckillRedisService;
    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @InjectMocks
    private SeckillService seckillService;

    @Test
    void shouldSubmitSeckillOrderAfterRedisPreDeductSuccess() {
        SeckillProduct seckillProduct = buildSeckillProduct();
        when(seckillProductMapper.selectById(1L)).thenReturn(seckillProduct);
        when(seckillRedisService.preDeduct(10L, seckillProduct)).thenReturn(SeckillRedisService.PreDeductResult.SUCCESS);
        when(snowflakeIdGenerator.nextOrderId(10L)).thenReturn(123456L);
        when(seckillOrderProducer.sendSeckillOrder(any())).thenReturn(true);

        Result<Long> result = seckillService.doSeckill(10L, 1L);

        assertEquals(200, result.getCode());
        assertEquals(123456L, result.getData());
        verify(seckillRedisService).markOrderProcessing(123456L);
    }

    @Test
    void shouldRollbackRedisReservationWhenKafkaSendFails() {
        SeckillProduct seckillProduct = buildSeckillProduct();
        when(seckillProductMapper.selectById(1L)).thenReturn(seckillProduct);
        when(seckillRedisService.preDeduct(10L, seckillProduct)).thenReturn(SeckillRedisService.PreDeductResult.SUCCESS);
        when(snowflakeIdGenerator.nextOrderId(10L)).thenReturn(123456L);
        when(seckillOrderProducer.sendSeckillOrder(any())).thenReturn(false);

        Result<Long> result = seckillService.doSeckill(10L, 1L);

        assertNull(result.getData());
        verify(seckillRedisService).releaseReservation(10L, 1L, 123456L);
    }

    @Test
    void shouldFallbackToDatabaseWhenRedisStatusMissing() {
        SeckillOrder order = new SeckillOrder();
        order.setId(99L);
        order.setStatus(SeckillOrderStatus.PAID.getCode());
        when(seckillRedisService.getOrderStatus(99L)).thenReturn(null);
        when(seckillOrderMapper.selectById(99L)).thenReturn(order);

        Result<Map<String, Object>> result = seckillService.getOrderStatus(99L);

        assertEquals(200, result.getCode());
        assertEquals("PAID", result.getData().get("status"));
        verify(seckillRedisService).syncOrderStatus(order);
    }

    @Test
    void shouldSendPayMessageForPendingOrder() {
        SeckillOrder order = new SeckillOrder();
        order.setId(77L);
        order.setUserId(9L);
        order.setStatus(SeckillOrderStatus.PENDING_PAYMENT.getCode());
        when(seckillOrderMapper.selectById(77L)).thenReturn(order);
        when(seckillOrderPayProducer.sendPayOrderMessage(any())).thenReturn(true);

        Result<String> result = seckillService.payOrder(9L, 77L);

        assertEquals(200, result.getCode());
        assertEquals("支付请求已受理", result.getData());
        verify(seckillRedisService).markOrderProcessing(77L);
    }

    private SeckillProduct buildSeckillProduct() {
        SeckillProduct seckillProduct = new SeckillProduct();
        seckillProduct.setId(1L);
        seckillProduct.setProductId(100L);
        seckillProduct.setSeckillPrice(BigDecimal.valueOf(88));
        seckillProduct.setSeckillStock(10);
        seckillProduct.setPurchaseLimit(1);
        seckillProduct.setStartTime(LocalDateTime.now().minusMinutes(1));
        seckillProduct.setEndTime(LocalDateTime.now().plusMinutes(10));
        return seckillProduct;
    }
}
