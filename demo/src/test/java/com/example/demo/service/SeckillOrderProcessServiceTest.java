package com.example.demo.service;

import com.example.demo.mapper.ProductMapper;
import com.example.demo.mapper.SeckillOrderMapper;
import com.example.demo.mapper.SeckillProductMapper;
import com.example.demo.model.dto.SeckillOrderMessage;
import com.example.demo.model.entity.SeckillOrder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeckillOrderProcessServiceTest {

    @Mock
    private SeckillOrderMapper seckillOrderMapper;
    @Mock
    private SeckillProductMapper seckillProductMapper;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private SeckillRedisService seckillRedisService;

    @InjectMocks
    private SeckillOrderProcessService seckillOrderProcessService;

    @Test
    void shouldCreateOrderAndPersistPendingPaymentStatus() {
        SeckillOrderMessage message = buildMessage();
        when(seckillOrderMapper.selectByUserAndSeckill(2L, 1L)).thenReturn(null);
        when(seckillProductMapper.decrStock(1L)).thenReturn(1);
        when(productMapper.decrStock(3L)).thenReturn(1);

        seckillOrderProcessService.processCreateOrder(message);

        verify(seckillOrderMapper).insertWithId(any(SeckillOrder.class));
        verify(seckillRedisService).markOrderCreated(2L, 1L, 1001L);
    }

    @Test
    void shouldReleaseRedisReservationWhenDbSeckillStockIsNotEnough() {
        SeckillOrderMessage message = buildMessage();
        when(seckillOrderMapper.selectByUserAndSeckill(2L, 1L)).thenReturn(null);
        when(seckillProductMapper.decrStock(1L)).thenReturn(0);

        seckillOrderProcessService.processCreateOrder(message);

        verify(seckillRedisService).releaseReservation(2L, 1L, 1001L);
        verify(seckillOrderMapper, never()).insertWithId(any());
    }

    @Test
    void shouldCompensateStocksWhenOrderInsertFails() {
        SeckillOrderMessage message = buildMessage();
        when(seckillOrderMapper.selectByUserAndSeckill(2L, 1L)).thenReturn(null);
        when(seckillProductMapper.decrStock(1L)).thenReturn(1);
        when(productMapper.decrStock(3L)).thenReturn(1);
        when(seckillOrderMapper.insertWithId(any(SeckillOrder.class))).thenThrow(new RuntimeException("insert failed"));

        assertThrows(RuntimeException.class, () -> seckillOrderProcessService.processCreateOrder(message));

        verify(productMapper).incrStock(3L);
        verify(seckillProductMapper).incrStock(1L);
        verify(seckillRedisService).releaseReservation(2L, 1L, 1001L);
    }

    @Test
    void shouldTreatDuplicateMessageAsIdempotentAndRestoreStocks() {
        SeckillOrderMessage message = buildMessage();
        when(seckillOrderMapper.selectByUserAndSeckill(2L, 1L)).thenReturn(null);
        when(seckillProductMapper.decrStock(1L)).thenReturn(1);
        when(productMapper.decrStock(3L)).thenReturn(1);
        when(seckillOrderMapper.insertWithId(any(SeckillOrder.class))).thenThrow(new DuplicateKeyException("duplicate"));

        assertDoesNotThrow(() -> seckillOrderProcessService.processCreateOrder(message));

        verify(productMapper).incrStock(3L);
        verify(seckillProductMapper).incrStock(1L);
        verify(seckillRedisService).markOrderCreated(2L, 1L, 1001L);
    }

    private SeckillOrderMessage buildMessage() {
        return new SeckillOrderMessage(1001L, 2L, 1L, 3L, BigDecimal.valueOf(66));
    }
}
