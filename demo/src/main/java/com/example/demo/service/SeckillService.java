package com.example.demo.service;

import com.example.demo.common.ErrorCode;
import com.example.demo.common.Result;
import com.example.demo.mapper.ProductMapper;
import com.example.demo.mapper.SeckillOrderMapper;
import com.example.demo.mapper.SeckillProductMapper;
import com.example.demo.model.entity.Product;
import com.example.demo.model.entity.SeckillOrder;
import com.example.demo.model.entity.SeckillProduct;
import com.example.demo.model.vo.SeckillProductVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class SeckillService {

    private static final String SECKILL_STOCK_KEY = "seckill:stock:";
    private static final String SECKILL_ORDER_KEY = "seckill:order:";

    @Autowired
    private SeckillProductMapper seckillProductMapper;

    @Autowired
    private SeckillOrderMapper seckillOrderMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

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
     * 执行秒杀
     * 使用Redis预减库存 + 数据库扣减
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<Long> doSeckill(Long userId, Long seckillId) {
        // 1. 检查秒杀活动是否存在
        SeckillProduct sp = seckillProductMapper.selectById(seckillId);
        if (sp == null) {
            return Result.fail(ErrorCode.PARAM_ERROR, "秒杀活动不存在");
        }

        // 2. 检查秒杀时间
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(sp.getStartTime())) {
            return Result.fail(ErrorCode.PARAM_ERROR, "秒杀尚未开始");
        }
        if (now.isAfter(sp.getEndTime())) {
            return Result.fail(ErrorCode.PARAM_ERROR, "秒杀已结束");
        }

        // 3. 检查是否重复下单（Redis）
        String orderKey = SECKILL_ORDER_KEY + seckillId + ":" + userId;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(orderKey))) {
            return Result.fail(ErrorCode.PARAM_ERROR, "不能重复秒杀");
        }

        // 4. Redis预减库存
        String stockKey = SECKILL_STOCK_KEY + seckillId;
        // 初始化库存到Redis（如果不存在）
        if (Boolean.FALSE.equals(redisTemplate.hasKey(stockKey))) {
            redisTemplate.opsForValue().set(stockKey, sp.getSeckillStock());
        }
        Long stock = redisTemplate.opsForValue().decrement(stockKey);
        if (stock == null || stock < 0) {
            // 库存不足，恢复
            redisTemplate.opsForValue().increment(stockKey);
            return Result.fail(ErrorCode.PARAM_ERROR, "库存不足，秒杀失败");
        }

        // 5. 数据库扣减库存
        int rows = seckillProductMapper.decrStock(seckillId);
        if (rows == 0) {
            // 数据库扣减失败，恢复Redis库存
            redisTemplate.opsForValue().increment(stockKey);
            return Result.fail(ErrorCode.PARAM_ERROR, "库存不足，秒杀失败");
        }

        // 同时扣减商品表库存
        productMapper.decrStock(sp.getProductId());

        // 6. 创建秒杀订单
        SeckillOrder order = new SeckillOrder();
        order.setUserId(userId);
        order.setSeckillId(seckillId);
        order.setProductId(sp.getProductId());
        order.setOrderPrice(sp.getSeckillPrice());
        order.setStatus(0); // 未支付
        seckillOrderMapper.insert(order);

        // 7. 标记已下单（防止重复）
        redisTemplate.opsForValue().set(orderKey, order.getId(), 24, TimeUnit.HOURS);

        log.info("秒杀成功: userId={}, seckillId={}, orderId={}", userId, seckillId, order.getId());
        return Result.success(order.getId());
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
