package com.example.demo.controller;

import com.example.demo.common.Result;
import com.example.demo.model.entity.Product;
import com.example.demo.model.vo.ProductVO;
import com.example.demo.service.ProductService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/product")
public class ProductController {

    @Autowired
    private ProductService productService;

    @Value("${server.port:8080}")
    private String serverPort;

    /**
     * 查询商品详情（带Redis缓存）
     * GET /api/product/{id}
     */
    @GetMapping("/{id}")
    public Result<ProductVO> getProductById(@PathVariable Long id) {
        log.info("[端口:{}] 查询商品详情: productId={}", serverPort, id);
        return productService.getProductById(id);
    }

    /**
     * 商品列表
     * GET /api/product/list?page=1&size=10
     */
    @GetMapping("/list")
    public Result<List<ProductVO>> getProductList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        log.info("[端口:{}] 查询商品列表: page={}, size={}", serverPort, page, size);
        return productService.getProductList(page, size);
    }

    /**
     * 新增商品
     * POST /api/product/add
     */
    @PostMapping("/add")
    public Result<Long> addProduct(@RequestBody Product product) {
        log.info("[端口:{}] 新增商品: name={}", serverPort, product.getProductName());
        return productService.addProduct(product);
    }

    /**
     * 清除商品缓存
     * POST /api/product/{id}/evict-cache
     */
    @PostMapping("/{id}/evict-cache")
    public Result<String> evictCache(@PathVariable Long id) {
        productService.evictProductCache(id);
        return Result.success("缓存已清除");
    }

    /**
     * 预热商品缓存
     * POST /api/product/{id}/warm-cache
     */
    @PostMapping("/{id}/warm-cache")
    public Result<String> warmCache(@PathVariable Long id) {
        productService.warmUpCache(id);
        return Result.success("缓存已预热");
    }

    /**
     * 服务器信息（用于验证负载均衡）
     * GET /api/product/server-info
     */
    @GetMapping("/server-info")
    public Result<String> serverInfo() {
        return Result.success("响应来自端口: " + serverPort);
    }
}
