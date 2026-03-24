package com.example.demo.kafka;

import com.example.demo.config.KafkaConfig;
import com.example.demo.mapper.ProductMapper;
import com.example.demo.mapper.SeckillOrderMapper;
import com.example.demo.mapper.SeckillProductMapper;
import com.example.demo.model.dto.SeckillOrderMessage;
import com.example.demo.model.entity.SeckillOrder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

/**
 * 秒杀订单消费者 - 异步处理订单创建（削峰填谷）
 */
@Slf4j
@Component
public class SeckillOrderConsumer {

    private static final String SECKILL_ORDER_KEY = "seckill:order:";
    private static final String SECKILL_ORDER_STATUS_KEY = "seckill:order:status:";

    @Autowired
    private SeckillOrderMapper seckillOrderMapper;

    @Autowired
    private SeckillProductMapper seckillProductMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @KafkaListener(topics = KafkaConfig.SECKILL_ORDER_TOPIC, groupId = "seckill-order-group")
    @Transactional(rollbackFor = Exception.class)
    public void consumeSeckillOrder(SeckillOrderMessage message) {
        log.info("收到秒杀订单消息: orderId={}, userId={}, seckillId={}",
                message.getOrderId(), message.getUserId(), message.getSeckillId());

        try {
            // 幂等检查：看DB中是否已存在该订单
            SeckillOrder existingOrder = seckillOrderMapper.selectByUserAndSeckill(
                    message.getUserId(), message.getSeckillId());
            if (existingOrder != null) {
                log.warn("订单已存在，跳过: userId={}, seckillId={}", message.getUserId(), message.getSeckillId());
                return;
            }

            // 数据库扣减秒杀库存
            int rows = seckillProductMapper.decrStock(message.getSeckillId());
            if (rows == 0) {
                log.warn("数据库库存不足: seckillId={}", message.getSeckillId());
                updateOrderStatus(message.getOrderId(), "FAILED");
                return;
            }

            // 扣减商品表库存
            productMapper.decrStock(message.getProductId());

            // 创建订单
            SeckillOrder order = new SeckillOrder();
            order.setId(message.getOrderId());
            order.setUserId(message.getUserId());
            order.setSeckillId(message.getSeckillId());
            order.setProductId(message.getProductId());
            order.setOrderPrice(message.getOrderPrice());
            order.setStatus(0); // 未支付
            seckillOrderMapper.insertWithId(order);

            // 更新Redis订单状态
            String orderKey = SECKILL_ORDER_KEY + message.getSeckillId() + ":" + message.getUserId();
            redisTemplate.opsForValue().set(orderKey, message.getOrderId(), 24, TimeUnit.HOURS);
            updateOrderStatus(message.getOrderId(), "SUCCESS");

            log.info("秒杀订单处理成功: orderId={}, userId={}", message.getOrderId(), message.getUserId());
        } catch (Exception e) {
            log.error("秒杀订单处理异常: orderId={}, error={}", message.getOrderId(), e.getMessage(), e);
            updateOrderStatus(message.getOrderId(), "FAILED");
            throw e; // 重新抛出让事务回滚
        }
    }

    private void updateOrderStatus(Long orderId, String status) {
        String statusKey = SECKILL_ORDER_STATUS_KEY + orderId;
        redisTemplate.opsForValue().set(statusKey, status, 1, TimeUnit.HOURS);
    }
}
