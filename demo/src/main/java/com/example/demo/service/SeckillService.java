package com.example.demo.service;

import com.example.demo.common.ErrorCode;
import com.example.demo.common.Result;
import com.example.demo.kafka.SeckillOrderProducer;
import com.example.demo.mapper.ProductMapper;
import com.example.demo.mapper.SeckillOrderMapper;
import com.example.demo.mapper.SeckillProductMapper;
import com.example.demo.model.dto.SeckillOrderMessage;
import com.example.demo.model.entity.Product;
import com.example.demo.model.entity.SeckillOrder;
import com.example.demo.model.entity.SeckillProduct;
import com.example.demo.model.vo.SeckillProductVO;
import com.example.demo.util.SnowflakeIdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class SeckillService {

    private static final String SECKILL_STOCK_KEY = "seckill:stock:";
    private static final String SECKILL_ORDER_KEY = "seckill:order:";
    private static final String SECKILL_ORDER_STATUS_KEY = "seckill:order:status:";

    /**
     * Lua脚本：原子性检查重复下单 + 预减库存
     * KEYS[1] = 订单标记key (seckill:order:{seckillId}:{userId})
     * KEYS[2] = 库存key (seckill:stock:{seckillId})
     * 返回: 0=成功, 1=重复下单, 2=库存不足
     */
    private static final String SECKILL_LUA_SCRIPT =
            "if redis.call('exists', KEYS[1]) == 1 then return 1 end " +
            "local stock = redis.call('decr', KEYS[2]) " +
            "if stock < 0 then redis.call('incr', KEYS[2]) return 2 end " +
            "redis.call('set', KEYS[1], 'PROCESSING', 'EX', 86400) " +
            "return 0";

    @Autowired
    private SeckillProductMapper seckillProductMapper;

    @Autowired
    private SeckillOrderMapper seckillOrderMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private SeckillOrderProducer seckillOrderProducer;

    @Autowired
    private SnowflakeIdGenerator snowflakeIdGenerator;

    /**
     * 获取秒杀商品列表
     */
    public Result<List<SeckillProductVO>> getSeckillList() {
        List<SeckillProduct> seckillProducts = seckillProductMapper.selectAll();
        List<SeckillProductVO> voList = new ArrayList<>();
        for (SeckillProduct sp : seckillProducts) {
            Product product = productMapper.selectById(sp.getProductId());
            if (product != null) {
                SeckillProductVO vo = buildSeckillVO(sp, product);
                voList.add(vo);
            }
        }
        return Result.success(voList);
    }

    /**
     * 获取秒杀商品详情
     */
    public Result<SeckillProductVO> getSeckillDetail(Long seckillId) {
        SeckillProduct sp = seckillProductMapper.selectById(seckillId);
        if (sp == null) {
            return Result.fail(ErrorCode.PARAM_ERROR, "秒杀活动不存在");
        }
        Product product = productMapper.selectById(sp.getProductId());
        if (product == null) {
            return Result.fail(ErrorCode.PARAM_ERROR, "商品不存在");
        }
        return Result.success(buildSeckillVO(sp, product));
    }

    /**
     * 执行秒杀（Kafka异步下单，削峰填谷）
     * 1. 校验秒杀时间
     * 2. Redis Lua脚本原子性：检查重复 + 预减库存（防止缓存击穿和超卖）
     * 3. 雪花算法生成订单ID
     * 4. 发送Kafka消息异步创建订单
     * 5. 返回订单ID，客户端轮询订单状态
     */
    public Result<Long> doSeckill(Long userId, Long seckillId) {
        // 1. 检查秒杀活动
        SeckillProduct sp = seckillProductMapper.selectById(seckillId);
        if (sp == null) {
            return Result.fail(ErrorCode.PARAM_ERROR, "秒杀活动不存在");
        }

        // 2. 检查秒杀时间
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        if (now.isBefore(sp.getStartTime())) {
            return Result.fail(ErrorCode.PARAM_ERROR, "秒杀尚未开始");
        }
        if (now.isAfter(sp.getEndTime())) {
            return Result.fail(ErrorCode.PARAM_ERROR, "秒杀已结束");
        }

        // 3. Redis Lua脚本原子操作：检查重复下单 + 预减库存
        String orderKey = SECKILL_ORDER_KEY + seckillId + ":" + userId;
        String stockKey = SECKILL_STOCK_KEY + seckillId;

        // 确保库存已预热到Redis
        if (Boolean.FALSE.equals(redisTemplate.hasKey(stockKey))) {
            redisTemplate.opsForValue().set(stockKey, sp.getSeckillStock());
        }

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(SECKILL_LUA_SCRIPT, Long.class);
        Long result = redisTemplate.execute(script, Arrays.asList(orderKey, stockKey));

        if (result == null) {
            return Result.fail(ErrorCode.SYSTEM_ERROR, "系统繁忙，请稍后重试");
        }
        if (result == 1L) {
            return Result.fail(ErrorCode.PARAM_ERROR, "不能重复秒杀");
        }
        if (result == 2L) {
            return Result.fail(ErrorCode.PARAM_ERROR, "库存不足，秒杀失败");
        }

        // 4. 雪花算法生成订单ID（基因法嵌入userId，支持按userId分库分表查询）
        long orderId = snowflakeIdGenerator.nextOrderId(userId);

        // 5. 发送Kafka消息异步处理订单
        SeckillOrderMessage message = new SeckillOrderMessage(
                orderId, userId, seckillId, sp.getProductId(), sp.getSeckillPrice());
        seckillOrderProducer.sendSeckillOrder(message);

        // 6. 设置订单状态为处理中
        String statusKey = SECKILL_ORDER_STATUS_KEY + orderId;
        redisTemplate.opsForValue().set(statusKey, "PROCESSING", 1, TimeUnit.HOURS);

        log.info("秒杀请求已受理: userId={}, seckillId={}, orderId={}", userId, seckillId, orderId);
        return Result.success(orderId);
    }

    /**
     * 查询订单状态（客户端轮询）
     */
    public Result<Map<String, Object>> getOrderStatus(Long orderId) {
        String statusKey = SECKILL_ORDER_STATUS_KEY + orderId;
        Object status = redisTemplate.opsForValue().get(statusKey);
        Map<String, Object> data = new HashMap<>();
        data.put("orderId", orderId);
        data.put("status", status != null ? status : "UNKNOWN");
        return Result.success(data);
    }

    /**
     * 按用户ID查询订单列表
     */
    public Result<List<SeckillOrder>> getOrdersByUserId(Long userId) {
        List<SeckillOrder> orders = seckillOrderMapper.selectByUserId(userId);
        return Result.success(orders);
    }

    /**
     * 按订单ID查询订单
     */
    public Result<SeckillOrder> getOrderById(Long orderId) {
        SeckillOrder order = seckillOrderMapper.selectById(orderId);
        if (order == null) {
            return Result.fail(ErrorCode.PARAM_ERROR, "订单不存在");
        }
        return Result.success(order);
    }

    /**
     * 预热秒杀库存到Redis
     */
    public void warmUpSeckillStock(Long seckillId) {
        SeckillProduct sp = seckillProductMapper.selectById(seckillId);
        if (sp != null) {
            String stockKey = SECKILL_STOCK_KEY + seckillId;
            redisTemplate.opsForValue().set(stockKey, sp.getSeckillStock());
            log.info("预热秒杀库存: seckillId={}, stock={}", seckillId, sp.getSeckillStock());
        }
    }

    private SeckillProductVO buildSeckillVO(SeckillProduct sp, Product product) {
        SeckillProductVO vo = new SeckillProductVO();
        vo.setId(sp.getId());
        vo.setProductId(sp.getProductId());
        vo.setProductName(product.getProductName());
        vo.setDescription(product.getDescription());
        vo.setImageUrl(product.getImageUrl());
        vo.setOriginalPrice(product.getPrice());
        vo.setSeckillPrice(sp.getSeckillPrice());
        vo.setSeckillStock(sp.getSeckillStock());
        vo.setStartTime(sp.getStartTime());
        vo.setEndTime(sp.getEndTime());
        vo.setStatus(sp.getStatus());
        return vo;
    }
}
