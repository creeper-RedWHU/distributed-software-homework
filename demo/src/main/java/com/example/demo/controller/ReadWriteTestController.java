package com.example.demo.controller;

import com.example.demo.annotation.ReadOnly;
import com.example.demo.common.Result;
import com.example.demo.config.DataSourceContextHolder;
import com.example.demo.mapper.ProductMapper;
import com.example.demo.model.entity.Product;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 读写分离测试控制器
 * 用于验证读写分离效果
 */
@Slf4j
@RestController
@RequestMapping("/api/rw-test")
public class ReadWriteTestController {

    @Autowired
    private ProductMapper productMapper;

    @Value("${server.port:8080}")
    private String serverPort;

    /**
     * 写入测试 - 使用主库
     * POST /api/rw-test/write
     */
    @PostMapping("/write")
    public Result<Map<String, Object>> testWrite(@RequestParam(defaultValue = "测试商品") String name,
                                                  @RequestParam(defaultValue = "99.99") BigDecimal price) {
        String currentDs = DataSourceContextHolder.getDataSource();
        log.info("[端口:{}] 写操作 -> 数据源: {}", serverPort, currentDs);

        Product product = new Product();
        product.setProductName(name + " (写于端口" + serverPort + ")");
        product.setDescription("读写分离写入测试");
        product.setPrice(price);
        product.setStock(100);
        product.setStatus(1);
        int rows = productMapper.insert(product);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("operation", "WRITE");
        result.put("datasource", currentDs);
        result.put("serverPort", serverPort);
        result.put("insertedId", product.getId());
        result.put("affectedRows", rows);
        return Result.success(result);
    }

    /**
     * 读取测试 - 使用从库（通过 @ReadOnly 注解自动路由）
     * GET /api/rw-test/read/{id}
     */
    @GetMapping("/read/{id}")
    @ReadOnly
    public Result<Map<String, Object>> testRead(@PathVariable Long id) {
        String currentDs = DataSourceContextHolder.getDataSource();
        log.info("[端口:{}] 读操作 -> 数据源: {}", serverPort, currentDs);

        Product product = productMapper.selectById(id);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("operation", "READ");
        result.put("datasource", currentDs);
        result.put("serverPort", serverPort);
        result.put("product", product);
        return Result.success(result);
    }

    /**
     * 手动切换测试 - 先写主库再读从库
     * POST /api/rw-test/write-then-read
     */
    @PostMapping("/write-then-read")
    public Result<Map<String, Object>> testWriteThenRead() {
        Map<String, Object> result = new LinkedHashMap<>();

        // 1. 写入主库（默认）
        DataSourceContextHolder.useMaster();
        String writeDs = DataSourceContextHolder.getDataSource();
        log.info("[端口:{}] 写操作 -> 数据源: {}", serverPort, writeDs);

        Product product = new Product();
        product.setProductName("读写分离测试-" + System.currentTimeMillis());
        product.setDescription("先写后读测试");
        product.setPrice(new BigDecimal("88.88"));
        product.setStock(50);
        product.setStatus(1);
        productMapper.insert(product);

        result.put("write_datasource", writeDs);
        result.put("write_productId", product.getId());

        // 2. 从从库读取
        DataSourceContextHolder.useSlave();
        String readDs = DataSourceContextHolder.getDataSource();
        log.info("[端口:{}] 读操作 -> 数据源: {}", serverPort, readDs);

        Product readProduct = productMapper.selectById(product.getId());

        result.put("read_datasource", readDs);
        result.put("read_product", readProduct);
        result.put("slave_has_data", readProduct != null);

        DataSourceContextHolder.clear();
        return Result.success(result);
    }

    /**
     * 查看当前数据源状态
     * GET /api/rw-test/status
     */
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("currentDataSource", DataSourceContextHolder.getDataSource());
        result.put("serverPort", serverPort);
        result.put("masterPool", "master-pool");
        result.put("slavePool", "slave-pool");
        return Result.success(result);
    }
}
