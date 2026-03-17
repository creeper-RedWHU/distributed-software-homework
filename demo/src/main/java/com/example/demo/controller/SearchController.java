package com.example.demo.controller;

import com.example.demo.common.Result;
import com.example.demo.model.es.ProductDocument;
import com.example.demo.service.ProductSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品搜索控制器 - ElasticSearch
 */
@Slf4j
@RestController
@RequestMapping("/api/search")
public class SearchController {

    @Autowired
    private ProductSearchService searchService;

    /**
     * 关键词搜索
     * GET /api/search/product?keyword=iPhone&page=1&size=10
     */
    @GetMapping("/product")
    public Result<List<ProductDocument>> search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        log.info("商品搜索: keyword='{}', page={}, size={}", keyword, page, size);
        return searchService.search(keyword, page, size);
    }

    /**
     * 高级搜索（关键词 + 价格区间）
     * GET /api/search/advanced?keyword=Apple&minPrice=1000&maxPrice=10000
     */
    @GetMapping("/advanced")
    public Result<List<ProductDocument>> advancedSearch(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        log.info("高级搜索: keyword='{}', price=[{},{}]", keyword, minPrice, maxPrice);
        return searchService.advancedSearch(keyword, minPrice, maxPrice, page, size);
    }

    /**
     * 同步所有商品到 ES
     * POST /api/search/sync
     */
    @PostMapping("/sync")
    public Result<Integer> syncAll() {
        log.info("开始同步商品到 ElasticSearch");
        return searchService.syncAllProducts();
    }
}
