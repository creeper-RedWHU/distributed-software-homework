# JMeter 压力测试

## 一、JMeter 安装

### macOS
```bash
brew install jmeter
```

### 通用方式
1. 下载 [Apache JMeter](https://jmeter.apache.org/download_jmeter.cgi)
2. 解压后运行 `bin/jmeter`（GUI模式）或 `bin/jmeter -n` （命令行模式）

## 二、测试计划说明

测试计划文件：`jmeter/seckill-test.jmx`

包含4个测试组：

| 测试组 | 线程数 | 持续时间 | 目标 |
|--------|-------|---------|------|
| 1-负载均衡测试 | 100 | 60s | 验证请求均匀分发 |
| 2-静态资源压测 | 200 | 30s | 测试Nginx提供静态资源的性能 |
| 3-商品详情API压测 | 100 | 30s | 测试Redis缓存效果 |
| 4-秒杀接口压测 | 500 | 一次性 | 测试高并发秒杀 |

## 三、运行测试

### 3.1 GUI 模式（可视化）

```bash
# 启动JMeter并打开测试计划
jmeter -t jmeter/seckill-test.jmx
```

在GUI中可以：
- 选择性启用/禁用测试组
- 实时观察聚合报告
- 查看结果树中的响应内容

### 3.2 命令行模式（推荐压测使用）

```bash
# 运行全部测试
jmeter -n -t jmeter/seckill-test.jmx -l result.jtl -e -o report/

# 参数说明：
# -n: 非GUI模式
# -t: 测试计划文件
# -l: 结果输出文件
# -e -o: 生成HTML报告到指定目录
```

## 四、测试1：负载均衡验证

### 4.1 测试配置
- 100个并发线程
- 持续60秒
- 请求 `/api/product/list` 和 `/api/product/server-info`

### 4.2 验证方法

**方法1：查看后端日志**
```bash
# 测试完成后，统计两个后端处理的请求数
echo "App1 请求数:"
docker logs seckill-app1 2>&1 | grep "查询商品列表" | wc -l

echo "App2 请求数:"
docker logs seckill-app2 2>&1 | grep "查询商品列表" | wc -l
```

轮询算法下，两个后端的请求数应大致相等（约50%:50%）。

**方法2：查看Nginx日志**
```bash
# 统计upstream分发情况
docker exec seckill-nginx cat /var/log/nginx/lb_access.log | \
  grep -oP 'upstream=\S+' | sort | uniq -c | sort -rn
```

### 4.3 预期结果

| 算法 | App1 请求比例 | App2 请求比例 |
|------|-------------|-------------|
| 轮询 | ~50% | ~50% |
| 加权(3:1) | ~75% | ~25% |
| 最少连接 | ~50% | ~50% |
| IP Hash | 100%或0% | 0%或100% |

## 五、测试2：静态资源 vs 动态API 对比

### 5.1 测试配置
- 200个并发线程
- 持续30秒
- 请求静态文件：`/index.html`、`/css/style.css`、`/js/app.js`

### 5.2 预期结果

| 请求类型 | 平均响应时间 | 吞吐量 |
|---------|------------|--------|
| 静态HTML | 1-5ms | 5000+ req/s |
| 静态CSS | 1-3ms | 8000+ req/s |
| 静态JS | 1-3ms | 8000+ req/s |
| 动态API | 10-50ms | 500-2000 req/s |

**结论**：静态资源由Nginx直接返回，性能远优于动态API请求。

### 5.3 分析方法

在JMeter聚合报告中对比：
- **Average（平均响应时间）**：静态资源远低于动态API
- **Throughput（吞吐量）**：静态资源远高于动态API
- **Error%（错误率）**：两者都应该接近0%

## 六、测试3：Redis 缓存效果

### 6.1 测试配置
- 100个并发线程
- 持续30秒
- 请求商品详情 `/api/product/1`、`/api/product/2`
- 包含不存在商品 `/api/product/99999`（穿透测试）

### 6.2 验证缓存命中

```bash
# 查看Redis缓存统计
docker exec seckill-redis redis-cli info stats | grep keyspace
docker exec seckill-redis redis-cli info stats | grep hits
docker exec seckill-redis redis-cli info stats | grep misses
```

### 6.3 对比有缓存和无缓存的性能

```bash
# 清除所有缓存
docker exec seckill-redis redis-cli flushdb

# 第一轮压测（缓存未命中，走DB）
jmeter -n -t jmeter/seckill-test.jmx -l result1.jtl

# 第二轮压测（缓存已命中）
jmeter -n -t jmeter/seckill-test.jmx -l result2.jtl

# 对比两次结果的平均响应时间
```

## 七、测试4：秒杀接口压测

### 7.1 测试配置
- 500个并发线程（模拟500个用户）
- 每人秒杀1次
- 秒杀活动库存100个

### 7.2 预期结果
- 500个请求中，约100个成功
- 约400个返回"库存不足"
- 没有超卖现象

### 7.3 验证

```bash
# 检查秒杀后库存
curl http://localhost/api/seckill/1
# seckillStock 应该为 0

# 检查数据库订单数
docker exec seckill-mysql mysql -uroot -proot seckill_db -e \
  "SELECT COUNT(*) FROM t_seckill_order WHERE seckill_id=1;"
# 应该为 100（等于初始库存）

# 检查Redis中的库存
docker exec seckill-redis redis-cli get "seckill:stock:1"
# 应该为 0
```

## 八、JMeter 报告解读

聚合报告关键指标：

| 指标 | 含义 | 关注点 |
|------|------|--------|
| Samples | 总请求数 | 越多说明吞吐能力越强 |
| Average | 平均响应时间(ms) | 越低越好 |
| Median | 中位数响应时间 | 比平均更能反映实际体验 |
| 90% Line | 90%请求的响应时间 | 尾部延迟 |
| 99% Line | 99%请求的响应时间 | 极端情况 |
| Error% | 错误率 | 应接近0% |
| Throughput | 吞吐量(req/s) | 越高越好 |
