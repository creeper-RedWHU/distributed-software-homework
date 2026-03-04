package com.example.demo.service;

import com.example.demo.common.Constants;
import com.example.demo.common.ErrorCode;
import com.example.demo.common.Result;
import com.example.demo.mapper.SeckillActivityMapper;
import com.example.demo.model.dto.SeckillOrderMessage;
import com.example.demo.model.entity.SeckillActivity;
import com.example.demo.model.vo.SeckillActivityVO;
import com.example.demo.model.vo.SeckillResultVO;
import com.example.demo.util.OrderNoGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillService {

    private final SeckillActivityMapper activityMapper;
    private final StockService stockService;
    private final ProductService productService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /** 本地内存售罄标记（避免无效 Redis 请求） */
    private final ConcurrentHashMap<Long, Boolean> soldOutMap = new ConcurrentHashMap<>();

    /**
     * 执行秒杀 — 核心方法
     */
    public Result<SeckillResultVO> execute(Long activityId, Long userId) {
        // 1. 本地售罄检查
        if (soldOutMap.getOrDefault(activityId, false)) {
            return Result.fail(ErrorCode.STOCK_EMPTY);
        }

        // 2. 活动状态校验
        SeckillActivity activity = getActivityFromCache(activityId);
        if (activity == null) {
            return Result.fail(ErrorCode.ACTIVITY_NOT_FOUND);
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(activity.getStartTime())) {
            return Result.fail(ErrorCode.ACTIVITY_NOT_STARTED);
        }
        if (now.isAfter(activity.getEndTime()) || activity.getStatus() == Constants.ACTIVITY_ENDED) {
            return Result.fail(ErrorCode.ACTIVITY_ENDED);
        }

        // 3. Redis Lua 原子扣减
        Long result = stockService.deductStockRedis(activityId, userId, activity.getLimitPerUser(), 1);
        if (result == null || result == -1) {
            soldOutMap.put(activityId, true);
            return Result.fail(ErrorCode.STOCK_EMPTY);
        }
        if (result == -2) {
            return Result.fail(ErrorCode.PURCHASE_LIMIT_EXCEEDED);
        }

        // 4. 生成订单号，发送 Kafka 消息
        String orderNo = OrderNoGenerator.generate();
        SeckillOrderMessage message = new SeckillOrderMessage();
        message.setOrderNo(orderNo);
        message.setUserId(userId);
        message.setActivityId(activityId);
        message.setProductId(activity.getProductId());
        message.setQuantity(1);
        message.setTotalAmount(activity.getSeckillPrice());
        message.setCreateTime(LocalDateTime.now());

        kafkaTemplate.send(Constants.TOPIC_SECKILL_ORDERS, String.valueOf(activityId), message);
        log.info("秒杀成功，消息已发送: orderNo={}, userId={}, activityId={}", orderNo, userId, activityId);

        return Result.success(new SeckillResultVO(orderNo, "QUEUING"));
    }

    /**
     * 从缓存获取活动信息
     */
    public SeckillActivity getActivityFromCache(Long activityId) {
        String key = Constants.REDIS_ACTIVITY_KEY + activityId;
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return (SeckillActivity) cached;
        }
        SeckillActivity activity = activityMapper.selectById(activityId);
        if (activity != null) {
            redisTemplate.opsForValue().set(key, activity, 5, java.util.concurrent.TimeUnit.MINUTES);
        }
        return activity;
    }

    /**
     * 获取活动列表
     */
    public List<SeckillActivityVO> listActivities(Integer status, int page, int size) {
        int offset = (page - 1) * size;
        List<SeckillActivity> activities;
        if (status != null) {
            activities = activityMapper.selectByStatus(status, offset, size);
        } else {
            activities = activityMapper.selectActiveActivities();
        }
        return activities.stream().map(this::toVO).collect(Collectors.toList());
    }

    /**
     * 获取活动详情
     */
    public SeckillActivityVO getActivityDetail(Long activityId) {
        SeckillActivity activity = getActivityFromCache(activityId);
        if (activity == null) return null;
        SeckillActivityVO vo = toVO(activity);
        vo.setServerTime(LocalDateTime.now());
        // 尝试使用 Redis 实时库存
        Integer redisStock = stockService.getRedisStock(activityId);
        if (redisStock != null) {
            vo.setAvailableStock(redisStock);
        }
        return vo;
    }

    /**
     * 创建秒杀活动
     */
    public SeckillActivity createActivity(com.example.demo.model.dto.ActivityCreateRequest request) {
        SeckillActivity activity = new SeckillActivity();
        activity.setName(request.getName());
        activity.setProductId(request.getProductId());
        activity.setSeckillPrice(request.getSeckillPrice());
        activity.setTotalStock(request.getTotalStock());
        activity.setAvailableStock(request.getTotalStock());
        activity.setStartTime(request.getStartTime());
        activity.setEndTime(request.getEndTime());
        activity.setStatus(Constants.ACTIVITY_NOT_STARTED);
        activity.setLimitPerUser(request.getLimitPerUser());
        activityMapper.insert(activity);
        log.info("秒杀活动创建: id={}, name={}", activity.getId(), activity.getName());
        return activity;
    }

    /**
     * 取消活动
     */
    public void cancelActivity(Long activityId) {
        activityMapper.updateStatus(activityId, Constants.ACTIVITY_CANCELLED);
        redisTemplate.delete(Constants.REDIS_ACTIVITY_KEY + activityId);
        redisTemplate.delete(Constants.REDIS_STOCK_KEY + activityId);
        soldOutMap.remove(activityId);
        log.info("秒杀活动取消: activityId={}", activityId);
    }

    /**
     * 清除售罄标记（库存回滚时调用）
     */
    public void clearSoldOutMark(Long activityId) {
        soldOutMap.remove(activityId);
    }

    private SeckillActivityVO toVO(SeckillActivity activity) {
        SeckillActivityVO vo = new SeckillActivityVO();
        vo.setActivityId(activity.getId());
        vo.setName(activity.getName());
        vo.setSeckillPrice(activity.getSeckillPrice());
        vo.setAvailableStock(activity.getAvailableStock());
        vo.setTotalStock(activity.getTotalStock());
        vo.setStartTime(activity.getStartTime());
        vo.setEndTime(activity.getEndTime());
        vo.setStatus(activity.getStatus());
        vo.setLimitPerUser(activity.getLimitPerUser());

        var product = productService.getById(activity.getProductId());
        if (product != null) {
            vo.setProductName(product.getName());
            vo.setOriginalPrice(product.getPrice());
            vo.setImageUrl(product.getImageUrl());
        }
        return vo;
    }
}
