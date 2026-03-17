# ElasticSearch 商品搜索

## 一、架构概述

```
Client ──► Nginx ──► App ──┬──► MySQL (CRUD)
                           ├──► Redis  (缓存)
                           └──► ES     (搜索)
```

- **MySQL**：商品数据的主存储
- **Redis**：商品详情页缓存
- **ElasticSearch**：全文检索，支持关键词搜索、价格区间过滤

## 二、核心实现

### 2.1 商品文档映射

```java
@Document(indexName = "products")
public class ProductDocument {
    @Id
    private Long id;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String productName;    // 全文检索

    @Field(type = FieldType.Text, analyzer = "standard")
    private String description;    // 全文检索

    @Field(type = FieldType.Double)
    private BigDecimal price;      // 范围过滤

    @Field(type = FieldType.Integer)
    private Integer stock;

    @Field(type = FieldType.Integer)
    private Integer status;        // 精确过滤
}
```

### 2.2 搜索服务

`ProductSearchService` 提供：

| 方法 | 说明 |
|------|------|
| `syncAllProducts()` | 从 MySQL 全量同步商品到 ES |
| `syncProduct(product)` | 单条商品同步 |
| `search(keyword, page, size)` | 关键词搜索（匹配商品名+描述） |
| `advancedSearch(keyword, minPrice, maxPrice, page, size)` | 高级搜索（关键词+价格区间） |

### 2.3 关键词搜索实现

```java
NativeSearchQuery query = new NativeSearchQueryBuilder()
    .withQuery(QueryBuilders.multiMatchQuery(keyword, "productName", "description"))
    .withPageable(PageRequest.of(page - 1, size))
    .build();
SearchHits<ProductDocument> hits = esTemplate.search(query, ProductDocument.class);
```

### 2.4 高级搜索（Bool Query）

```java
BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
boolQuery.must(QueryBuilders.multiMatchQuery(keyword, "productName", "description"));
boolQuery.filter(QueryBuilders.rangeQuery("price").gte(minPrice).lte(maxPrice));
boolQuery.filter(QueryBuilders.termQuery("status", 1));
```

## 三、API 接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/search/product?keyword=iPhone` | GET | 关键词搜索 |
| `/api/search/advanced?keyword=Apple&minPrice=1000&maxPrice=10000` | GET | 高级搜索 |
| `/api/search/sync` | POST | 全量同步 MySQL → ES |

## 四、Docker 部署

### 4.1 启动完整环境

```bash
cd demo
docker-compose -f docker-compose.full.yml up -d --build
```

ES 容器配置：
```yaml
elasticsearch:
  image: docker.elastic.co/elasticsearch/elasticsearch:7.17.16
  environment:
    - discovery.type=single-node
    - ES_JAVA_OPTS=-Xms512m -Xmx512m
    - xpack.security.enabled=false
  ports:
    - "9200:9200"
```

### 4.2 验证 ES 状态

```bash
# 检查集群健康
curl http://localhost:9200/_cluster/health?pretty

# 查看索引列表
curl http://localhost:9200/_cat/indices?v
```

## 五、使用步骤

### 5.1 同步数据

ES 启动后，需先将 MySQL 中的商品数据同步到 ES：

```bash
# 同步所有商品
curl -X POST http://localhost/api/search/sync
# 返回: {"code":200,"data":5}  (同步了5条商品)
```

### 5.2 关键词搜索

```bash
# 搜索 "iPhone"
curl "http://localhost/api/search/product?keyword=iPhone"

# 搜索 "Apple"
curl "http://localhost/api/search/product?keyword=Apple"

# 搜索 "Pro" (匹配 iPhone 15 Pro 和 MacBook Pro)
curl "http://localhost/api/search/product?keyword=Pro"
```

### 5.3 高级搜索

```bash
# 价格 1000-5000 的 Apple 产品
curl "http://localhost/api/search/advanced?keyword=Apple&minPrice=1000&maxPrice=5000"

# 所有价格 5000 以上的商品
curl "http://localhost/api/search/advanced?minPrice=5000"
```

### 5.4 查看 ES 索引数据

```bash
# 查看 products 索引的所有文档
curl "http://localhost:9200/products/_search?pretty"

# 查看索引映射
curl "http://localhost:9200/products/_mapping?pretty"

# 文档数量
curl "http://localhost:9200/products/_count?pretty"
```

## 六、ES vs MySQL 搜索对比

| 特性 | MySQL LIKE | ElasticSearch |
|------|-----------|---------------|
| 全文搜索 | 不支持（仅前缀匹配） | 支持分词、相关性排序 |
| 搜索性能 | 全表扫描，O(n) | 倒排索引，O(1) |
| 中文支持 | 需 FULLTEXT INDEX | 支持 IK 分词器 |
| 多条件组合 | SQL WHERE | Bool Query 灵活组合 |
| 适用场景 | 精确查询 | 模糊搜索、全文检索 |

## 七、配置说明

### application.yml

```yaml
spring:
  elasticsearch:
    uris: http://localhost:9200
    connection-timeout: 3s
    socket-timeout: 10s
```

### Docker 环境变量

```yaml
SPRING_ELASTICSEARCH_URIS: http://elasticsearch:9200
```

## 八、注意事项

1. **数据一致性**：ES 中的数据需要手动或定时同步，与 MySQL 可能存在延迟
2. **ES 未启动时**：搜索接口会返回错误提示，不影响其他功能正常运行
3. **中文搜索**：默认使用 standard 分析器，如需更好的中文分词效果，可安装 IK 分词器插件
4. **内存需求**：ES 至少需要 512MB JVM 堆内存，生产环境建议 2GB+
