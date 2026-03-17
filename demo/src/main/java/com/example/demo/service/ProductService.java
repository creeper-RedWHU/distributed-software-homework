package com.example.demo.service;

import com.example.demo.common.ErrorCode;
import com.example.demo.common.Result;
import com.example.demo.mapper.ProductMapper;
import com.example.demo.model.entity.Product;
import com.example.demo.model.vo.ProductVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 商品服务 - 带Redis缓存
 * 处理缓存穿透、击穿、雪崩
 */
@Slf4j
@Service
public class ProductService {

    private static final String PRODUCT_CACHE_KEY = "product:detail:";
    private static final String PRODUCT_NULL_KEY = "product:null:";
    private static final String PRODUCT_LOCK_KEY = "product:lock:";
    private static final long CACHE_TTL_SECONDS = 3600; // 基础TTL 1小时
    private static final long NULL_TTL_SECONDS = 300;   // 空值缓存5分钟
    private static final long LOCK_TTL_SECONDS = 10;    // 互斥锁10秒

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 查询商品详情（带缓存）
     * - 缓存穿透：对不存在的商品缓存空值
     * - 缓存击穿：使用互斥锁防止热点key失效后大量请求穿透到DB
     * - 缓存雪崩：TTL加随机偏移，避免同时过期
     */
    public Result<ProductVO> getProductById(Long id) {
        String cacheKey = PRODUCT_CACHE_KEY + id;
        String nullKey = PRODUCT_NULL_KEY + id;

        // 1. 查询缓存
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.debug("缓存命中: productId={}", id);
            ProductVO vo = convertCachedToVO(cached);
            return Result.success(vo);
        }

        // 2. 检查空值缓存（防止缓存穿透）
        if (Boolean.TRUE.equals(redisTemplate.hasKey(nullKey))) {
            log.debug("命中空值缓存，商品不存在: productId={}", id);
            return Result.fail(ErrorCode.PARAM_ERROR, "商品不存在");
        }

        // 3. 缓存未命中，使用互斥锁查询DB（防止缓存击穿）
        String lockKey = PRODUCT_LOCK_KEY + id;
        ProductVO vo = null;
        try {
            boolean locked = tryLock(lockKey);
            if (!locked) {
                // 未获取到锁，短暂等待后重试
                Thread.sleep(50);
                return getProductById(id);
            }

            // 双重检查：再次查缓存
            cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                vo = convertCachedToVO(cached);
                return Result.success(vo);
            }

            // 查询数据库
            Product product = productMapper.selectById(id);
            if (product == null) {
                // 缓存空值（防止缓存穿透）
                redisTemplate.opsForValue().set(nullKey, "NULL", NULL_TTL_SECONDS, TimeUnit.SECONDS);
                log.info("商品不存在，缓存空值: productId={}", id);
                return Result.fail(ErrorCode.PARAM_ERROR, "商品不存在");
            }

            vo = convertToVO(product);

            // 写入缓存（TTL加随机偏移，防止缓存雪崩）
            long ttl = CACHE_TTL_SECONDS + ThreadLocalRandom.current().nextLong(0, 600);
            redisTemplate.opsForValue().set(cacheKey, vo, ttl, TimeUnit.SECONDS);
            log.info("缓存商品详情: productId={}, ttl={}s", id, ttl);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.fail(ErrorCode.SYSTEM_ERROR, "系统繁忙，请稍后重试");
        } finally {
            unlock(lockKey);
        }

        return Result.success(vo);
    }

    /**
     * 查询商品列表（不缓存，直接查DB）
     */
    public Result<List<ProductVO>> getProductList(Integer page, Integer size) {
        int offset = (page - 1) * size;
        List<Product> products = productMapper.selectList(offset, size);
        List<ProductVO> voList = products.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        return Result.success(voList);
    }

    /**
     * 新增商品
     */
    public Result<Long> addProduct(Product product) {
        product.setStatus(1);
        int rows = productMapper.insert(product);
        if (rows > 0) {
            log.info("新增商品成功: productId={}, name={}", product.getId(), product.getProductName());
            return Result.success(product.getId());
        }
        return Result.fail(ErrorCode.SYSTEM_ERROR, "新增商品失败");
    }

    /**
     * 清除商品缓存
     */
    public void evictProductCache(Long id) {
        String cacheKey = PRODUCT_CACHE_KEY + id;
        String nullKey = PRODUCT_NULL_KEY + id;
        redisTemplate.delete(cacheKey);
        redisTemplate.delete(nullKey);
        log.info("清除商品缓存: productId={}", id);
    }

    /**
     * 预热商品缓存
     */
    public void warmUpCache(Long id) {
        Product product = productMapper.selectById(id);
        if (product != null) {
            String cacheKey = PRODUCT_CACHE_KEY + id;
            long ttl = CACHE_TTL_SECONDS + ThreadLocalRandom.current().nextLong(0, 600);
            redisTemplate.opsForValue().set(cacheKey, convertToVO(product), ttl, TimeUnit.SECONDS);
            log.info("预热商品缓存: productId={}", id);
        }
    }

    private boolean tryLock(String lockKey) {
        Boolean result = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", LOCK_TTL_SECONDS, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(result);
    }

    private void unlock(String lockKey) {
        redisTemplate.delete(lockKey);
    }

    private ProductVO convertToVO(Product product) {
        ProductVO vo = new ProductVO();
        BeanUtils.copyProperties(product, vo);
        return vo;
    }

    private ProductVO convertCachedToVO(Object cached) {
        if (cached instanceof ProductVO) {
            return (ProductVO) cached;
        }
        // JSON反序列化可能返回LinkedHashMap
        if (cached instanceof java.util.Map) {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> map = (java.util.Map<String, Object>) cached;
            ProductVO vo = new ProductVO();
            if (map.get("id") != null) vo.setId(((Number) map.get("id")).longValue());
            if (map.get("productName") != null) vo.setProductName((String) map.get("productName"));
            if (map.get("description") != null) vo.setDescription((String) map.get("description"));
            if (map.get("imageUrl") != null) vo.setImageUrl((String) map.get("imageUrl"));
            if (map.get("price") != null) vo.setPrice(new java.math.BigDecimal(map.get("price").toString()));
            if (map.get("stock") != null) vo.setStock(((Number) map.get("stock")).intValue());
            if (map.get("status") != null) vo.setStatus(((Number) map.get("status")).intValue());
            return vo;
        }
        return null;
    }
}
