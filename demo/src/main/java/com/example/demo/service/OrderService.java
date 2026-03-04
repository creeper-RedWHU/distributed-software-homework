package com.example.demo.service;

import com.example.demo.common.Constants;
import com.example.demo.mapper.OrderMapper;
import com.example.demo.model.dto.SeckillOrderMessage;
import com.example.demo.model.entity.Order;
import com.example.demo.model.vo.OrderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderMapper orderMapper;
    private final StockService stockService;

    @Value("${seckill.order-expire-minutes:15}")
    private int orderExpireMinutes;

    /**
     * 创建秒杀订单（Kafka 消费者调用）
     */
    @Transactional
    public Order createOrder(SeckillOrderMessage message) {
        // 幂等检查
        if (orderMapper.countByOrderNo(message.getOrderNo()) > 0) {
            log.warn("重复订单，跳过: {}", message.getOrderNo());
            return null;
        }

        Order order = new Order();
        order.setOrderNo(message.getOrderNo());
        order.setUserId(message.getUserId());
        order.setActivityId(message.getActivityId());
        order.setProductId(message.getProductId());
        order.setQuantity(message.getQuantity());
        order.setTotalAmount(message.getTotalAmount());
        order.setStatus(Constants.ORDER_STATUS_UNPAID);
        order.setExpireTime(LocalDateTime.now().plusMinutes(orderExpireMinutes));

        orderMapper.insert(order);
        log.info("订单创建成功: orderNo={}, userId={}", order.getOrderNo(), order.getUserId());
        return order;
    }

    /**
     * 支付订单
     */
    @Transactional
    public boolean payOrder(String orderNo) {
        int rows = orderMapper.updateStatusWithPayTime(orderNo,
                Constants.ORDER_STATUS_UNPAID, Constants.ORDER_STATUS_PAID);
        if (rows > 0) {
            log.info("订单支付成功: {}", orderNo);
            return true;
        }
        return false;
    }

    /**
     * 取消订单（触发库存回滚）
     */
    @Transactional
    public boolean cancelOrder(String orderNo) {
        Order order = orderMapper.selectByOrderNo(orderNo);
        if (order == null || order.getStatus() != Constants.ORDER_STATUS_UNPAID) {
            return false;
        }

        int rows = orderMapper.updateStatus(orderNo,
                Constants.ORDER_STATUS_UNPAID, Constants.ORDER_STATUS_CANCELLED);
        if (rows > 0) {
            // 库存回滚
            stockService.rollbackStockRedis(order.getActivityId(), order.getUserId(), order.getQuantity());
            stockService.rollbackStockMySQL(order.getActivityId(), order.getQuantity());
            stockService.logStockChange(order.getActivityId(), orderNo,
                    order.getUserId(), order.getQuantity(), Constants.STOCK_LOG_ROLLBACK);
            log.info("订单取消成功，库存已回滚: {}", orderNo);
            return true;
        }
        return false;
    }

    public Order getByOrderNo(String orderNo) {
        return orderMapper.selectByOrderNo(orderNo);
    }

    public List<Order> getByUserId(Long userId, Integer status, int page, int size) {
        int offset = (page - 1) * size;
        if (status != null) {
            return orderMapper.selectByUserIdAndStatus(userId, status, offset, size);
        }
        return orderMapper.selectByUserId(userId, offset, size);
    }

    /**
     * 关闭过期订单（定时任务调用）
     */
    @Transactional
    public int closeExpiredOrders() {
        List<Order> expiredOrders = orderMapper.selectExpiredOrders(100);
        int count = 0;
        for (Order order : expiredOrders) {
            int rows = orderMapper.updateStatus(order.getOrderNo(),
                    Constants.ORDER_STATUS_UNPAID, Constants.ORDER_STATUS_TIMEOUT);
            if (rows > 0) {
                stockService.rollbackStockRedis(order.getActivityId(), order.getUserId(), order.getQuantity());
                stockService.rollbackStockMySQL(order.getActivityId(), order.getQuantity());
                stockService.logStockChange(order.getActivityId(), order.getOrderNo(),
                        order.getUserId(), order.getQuantity(), Constants.STOCK_LOG_ROLLBACK);
                count++;
            }
        }
        if (count > 0) {
            log.info("关闭过期订单: {} 个", count);
        }
        return count;
    }

    public int countByActivityId(Long activityId) {
        return orderMapper.countByActivityId(activityId);
    }

    /**
     * 将 Order 转为 OrderVO
     */
    public OrderVO toVO(Order order) {
        OrderVO vo = new OrderVO();
        vo.setOrderNo(order.getOrderNo());
        vo.setQuantity(order.getQuantity());
        vo.setTotalAmount(order.getTotalAmount());
        vo.setStatus(order.getStatus());
        vo.setStatusText(getStatusText(order.getStatus()));
        vo.setExpireTime(order.getExpireTime());
        vo.setCreatedAt(order.getCreatedAt());
        return vo;
    }

    private String getStatusText(int status) {
        return switch (status) {
            case Constants.ORDER_STATUS_UNPAID -> "待支付";
            case Constants.ORDER_STATUS_PAID -> "已支付";
            case Constants.ORDER_STATUS_CANCELLED -> "已取消";
            case Constants.ORDER_STATUS_REFUNDED -> "已退款";
            case Constants.ORDER_STATUS_TIMEOUT -> "已超时";
            default -> "未知";
        };
    }
}
