package com.example.demo.service;

import com.example.demo.mapper.ProductMapper;
import com.example.demo.mapper.SeckillOrderMapper;
import com.example.demo.mapper.SeckillProductMapper;
import com.example.demo.model.dto.SeckillOrderMessage;
import com.example.demo.model.entity.SeckillOrder;
import com.example.demo.model.enums.SeckillOrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillOrderProcessService {

    private final SeckillOrderMapper seckillOrderMapper;
    private final SeckillProductMapper seckillProductMapper;
    private final ProductMapper productMapper;
    private final SeckillRedisService seckillRedisService;

    @Transactional(rollbackFor = Exception.class)
    public void processCreateOrder(SeckillOrderMessage message) {
        log.info("开始处理秒杀下单消息: orderId={}, userId={}, seckillId={}",
                message.getOrderId(), message.getUserId(), message.getSeckillId());

        SeckillOrder existingOrder = seckillOrderMapper.selectByUserAndSeckill(
                message.getUserId(), message.getSeckillId());
        if (existingOrder != null) {
            seckillRedisService.markOrderCreated(existingOrder.getUserId(), existingOrder.getSeckillId(), existingOrder.getId());
            return;
        }

        if (seckillProductMapper.decrStock(message.getSeckillId()) == 0) {
            log.warn("数据库秒杀库存不足，执行回补: seckillId={}, orderId={}", message.getSeckillId(), message.getOrderId());
            seckillRedisService.releaseReservation(message.getUserId(), message.getSeckillId(), message.getOrderId());
            return;
        }

        if (productMapper.decrStock(message.getProductId()) == 0) {
            log.warn("商品库存不足，执行补偿: productId={}, orderId={}", message.getProductId(), message.getOrderId());
            seckillProductMapper.incrStock(message.getSeckillId());
            seckillRedisService.releaseReservation(message.getUserId(), message.getSeckillId(), message.getOrderId());
            return;
        }

        try {
            SeckillOrder order = new SeckillOrder();
            order.setId(message.getOrderId());
            order.setUserId(message.getUserId());
            order.setSeckillId(message.getSeckillId());
            order.setProductId(message.getProductId());
            order.setOrderPrice(message.getOrderPrice());
            order.setStatus(SeckillOrderStatus.PENDING_PAYMENT.getCode());
            seckillOrderMapper.insertWithId(order);
            seckillRedisService.markOrderCreated(message.getUserId(), message.getSeckillId(), message.getOrderId());
            log.info("秒杀订单创建成功: orderId={}", message.getOrderId());
        } catch (DuplicateKeyException duplicateKeyException) {
            log.warn("订单重复消费，按幂等成功处理: orderId={}", message.getOrderId());
            productMapper.incrStock(message.getProductId());
            seckillProductMapper.incrStock(message.getSeckillId());
            seckillRedisService.markOrderCreated(message.getUserId(), message.getSeckillId(), message.getOrderId());
        } catch (Exception ex) {
            compensateCreateOrderFailure(message);
            throw ex;
        }
    }

    private void compensateCreateOrderFailure(SeckillOrderMessage message) {
        log.error("订单创建失败，执行库存与Redis补偿: orderId={}", message.getOrderId());
        productMapper.incrStock(message.getProductId());
        seckillProductMapper.incrStock(message.getSeckillId());
        seckillRedisService.releaseReservation(message.getUserId(), message.getSeckillId(), message.getOrderId());
    }
}
