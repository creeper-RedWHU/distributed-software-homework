# Spring Cloud Alibaba Nacos 实验工程

这个目录提供一套独立的实验工程，覆盖以下目标：

- 使用 Nacos 完成服务注册发现与配置管理
- 使用 Spring Cloud Gateway 通过网关地址调用服务
- 验证基于服务名的动态路由
- 在代码中读取 Nacos 属性，并验证动态刷新
- 使用 Sentinel 实现限流、熔断和降级
- 使用 JMeter 对网关入口进行压力测试

## 目录结构

```text
cloud-lab
├── docker-compose.yml
├── gateway-service
├── jmeter
├── nacos-config
├── scripts
└── stock-service
```

## 环境启动

1. 启动 Nacos 和 Sentinel Dashboard

```bash
cd cloud-lab
docker compose up -d
```

2. 发布示例配置到 Nacos

```bash
chmod +x scripts/publish-nacos-config.sh
./scripts/publish-nacos-config.sh
```

3. 启动两个 `stock-service` 实例

```bash
mvn -pl stock-service spring-boot:run -Dspring-boot.run.profiles=node1
mvn -pl stock-service spring-boot:run -Dspring-boot.run.profiles=node2
```

4. 启动网关

```bash
mvn -pl gateway-service spring-boot:run
```

## 核心验证点

### 1. 服务注册与动态路由

直接通过网关按服务名访问：

```bash
curl "http://127.0.0.1:8088/stock-service/api/stocks/echo?from=discovery"
```

通过自定义网关路由访问：

```bash
curl "http://127.0.0.1:8088/stock/api/stocks/echo?from=custom-route"
```

如果多次调用返回的 `servedBy` 在 `9001` 和 `9002` 之间变化，说明 Nacos 注册发现和 Gateway 负载均衡正常。

### 2. Nacos 配置动态刷新

先查看配置：

```bash
curl http://127.0.0.1:8088/stock/api/stocks/config
```

然后在 Nacos 控制台将 `stock-service.yaml` 修改为：

```yaml
demo:
  message: nacos-message-v2
  owner: teacher-check
```

再次请求同一接口，返回值会直接变化，不需要重启服务。

### 3. 流量治理

`stock-service-sentinel-flow.json` 控制 `stockHotspot` 资源的 QPS。

```bash
curl "http://127.0.0.1:8088/stock/api/stocks/hot?instanceId=load-test"
```

高并发时会出现：

```json
{"code":429,"message":"gateway rate limit triggered"}
```

或服务端 Sentinel 的拦截响应：

```json
{"event":"blocked","message":"sentinel flow control triggered"}
```

### 4. 熔断与降级

调用慢接口触发超时：

```bash
curl "http://127.0.0.1:8088/stock/api/stocks/slow?delayMs=3000"
```

当超时超过 `gateway-service` 中 `stockCircuit` 的阈值后，网关会返回 `/fallback/stock` 的降级结果。

## JMeter 压测

打开 [cloud-governance-test.jmx](/Users/mac/Desktop/project/distributed-software-homework/cloud-lab/jmeter/cloud-governance-test.jmx)，或命令行执行：

```bash
jmeter -n -t jmeter/cloud-governance-test.jmx -l jmeter/result.jtl
```

预期现象：

- 在没有限流规则时，大部分请求返回 200
- 发布 `gateway-service-sentinel-gateway.json` 后，网关开始出现 429
- 将 `stock-service-sentinel-flow.json` 的 `count` 调小后，服务端拦截比例上升
- 压测 `slow` 接口时，网关 fallback 响应比例上升

## Nacos 配置说明

| Data ID | 用途 |
| --- | --- |
| `stock-service.yaml` | 动态业务配置 |
| `stock-service-sentinel-flow.json` | 服务端限流规则 |
| `stock-service-sentinel-degrade.json` | 服务端降级规则 |
| `gateway-service-sentinel-gateway.json` | 网关入口限流规则 |

## 注意事项

- `stock-service` 使用同一个服务名启动两个实例，便于观察 Gateway 的负载均衡效果。
- 本工程默认使用 `public` namespace 和 `DEFAULT_GROUP`。
- 如果本地 Maven 没有缓存 Spring Cloud Alibaba 依赖，首次构建需要联网拉取。
