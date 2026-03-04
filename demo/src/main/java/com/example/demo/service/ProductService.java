package com.example.demo.service;

import com.example.demo.mapper.ProductMapper;
import com.example.demo.model.dto.ProductCreateRequest;
import com.example.demo.model.entity.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.example.demo.common.Constants.REDIS_PRODUCT_KEY;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductMapper productMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    public Product getById(Long id) {
        String key = REDIS_PRODUCT_KEY + id;
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return (Product) cached;
        }
        Product product = productMapper.selectById(id);
        if (product != null) {
            redisTemplate.opsForValue().set(key, product, 10, TimeUnit.MINUTES);
        }
        return product;
    }

    public List<Product> listByCategory(Long categoryId, int page, int size) {
        int offset = (page - 1) * size;
        if (categoryId != null) {
            return productMapper.selectByCategory(categoryId, offset, size);
        }
        return productMapper.selectAll(offset, size);
    }

    public Product create(ProductCreateRequest request) {
        Product product = new Product();
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setImageUrl(request.getImageUrl());
        product.setCategoryId(request.getCategoryId());
        product.setStatus(1);
        productMapper.insert(product);
        log.info("商品创建成功: id={}, name={}", product.getId(), product.getName());
        return product;
    }

    public void updateStatus(Long id, Integer status) {
        productMapper.updateStatus(id, status);
        redisTemplate.delete(REDIS_PRODUCT_KEY + id);
        log.info("商品状态更新: id={}, status={}", id, status);
    }
}
