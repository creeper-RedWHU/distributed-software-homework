package com.example.demo.service;

import com.example.demo.common.Result;
import com.example.demo.mapper.ProductMapper;
import com.example.demo.model.entity.Product;
import com.example.demo.model.es.ProductDocument;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 商品搜索服务 - ElasticSearch
 */
@Slf4j
@Service
public class ProductSearchService {

    @Autowired(required = false)
    private ElasticsearchRestTemplate esTemplate;

    @Autowired
    private ProductMapper productMapper;

    private boolean isEsAvailable() {
        return esTemplate != null;
    }

    /**
     * 同步全部商品到 ES
     */
    public Result<Integer> syncAllProducts() {
        if (!isEsAvailable()) {
            return Result.fail("ElasticSearch 服务未启用");
        }
        try {
            List<Product> products = productMapper.selectList(0, 10000);
            List<ProductDocument> docs = products.stream()
                    .map(this::toDocument)
                    .collect(Collectors.toList());
            esTemplate.save(docs);
            log.info("同步 {} 条商品到 ElasticSearch", docs.size());
            return Result.success(docs.size());
        } catch (Exception e) {
            log.error("同步商品到ES失败", e);
            return Result.fail("同步失败: " + e.getMessage());
        }
    }

    /**
     * 同步单个商品到 ES
     */
    public void syncProduct(Product product) {
        if (!isEsAvailable()) return;
        try {
            esTemplate.save(toDocument(product));
            log.info("同步商品到ES: id={}", product.getId());
        } catch (Exception e) {
            log.warn("同步商品到ES失败: id={}, error={}", product.getId(), e.getMessage());
        }
    }

    /**
     * 关键词搜索
     */
    public Result<List<ProductDocument>> search(String keyword, int page, int size) {
        if (!isEsAvailable()) {
            return Result.fail("ElasticSearch 服务未启用");
        }
        try {
            NativeSearchQuery query = new NativeSearchQueryBuilder()
                    .withQuery(QueryBuilders.multiMatchQuery(keyword, "productName", "description"))
                    .withPageable(PageRequest.of(page - 1, size))
                    .build();

            SearchHits<ProductDocument> hits = esTemplate.search(query, ProductDocument.class);
            List<ProductDocument> results = hits.stream()
                    .map(SearchHit::getContent)
                    .collect(Collectors.toList());

            log.info("搜索关键词: '{}', 结果数: {}, 总命中: {}", keyword, results.size(), hits.getTotalHits());
            return Result.success(results);
        } catch (Exception e) {
            log.error("ES搜索失败: keyword={}", keyword, e);
            return Result.fail("搜索失败: " + e.getMessage());
        }
    }

    /**
     * 高级搜索（关键词 + 价格区间）
     */
    public Result<List<ProductDocument>> advancedSearch(String keyword, BigDecimal minPrice,
                                                         BigDecimal maxPrice, int page, int size) {
        if (!isEsAvailable()) {
            return Result.fail("ElasticSearch 服务未启用");
        }
        try {
            BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();

            if (keyword != null && !keyword.isEmpty()) {
                boolQuery.must(QueryBuilders.multiMatchQuery(keyword, "productName", "description"));
            }
            if (minPrice != null) {
                boolQuery.filter(QueryBuilders.rangeQuery("price").gte(minPrice.doubleValue()));
            }
            if (maxPrice != null) {
                boolQuery.filter(QueryBuilders.rangeQuery("price").lte(maxPrice.doubleValue()));
            }
            boolQuery.filter(QueryBuilders.termQuery("status", 1));

            NativeSearchQuery query = new NativeSearchQueryBuilder()
                    .withQuery(boolQuery)
                    .withPageable(PageRequest.of(page - 1, size))
                    .build();

            SearchHits<ProductDocument> hits = esTemplate.search(query, ProductDocument.class);
            List<ProductDocument> results = hits.stream()
                    .map(SearchHit::getContent)
                    .collect(Collectors.toList());

            log.info("高级搜索: keyword='{}', price=[{},{}], 结果数: {}",
                    keyword, minPrice, maxPrice, results.size());
            return Result.success(results);
        } catch (Exception e) {
            log.error("ES高级搜索失败", e);
            return Result.fail("搜索失败: " + e.getMessage());
        }
    }

    /**
     * 删除ES中的商品
     */
    public void deleteProduct(Long id) {
        if (!isEsAvailable()) return;
        try {
            esTemplate.delete(String.valueOf(id), ProductDocument.class);
        } catch (Exception e) {
            log.warn("删除ES商品失败: id={}", id);
        }
    }

    private ProductDocument toDocument(Product product) {
        ProductDocument doc = new ProductDocument();
        BeanUtils.copyProperties(product, doc);
        return doc;
    }
}
