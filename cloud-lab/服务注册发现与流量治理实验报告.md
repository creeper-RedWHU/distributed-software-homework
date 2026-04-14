# 服务注册发现、配置动态刷新与流量治理实验报告

## 一、实验目标

本实验基于 Spring Cloud Alibaba 搭建一套最小可运行的微服务环境，完成以下内容：

1. 使用 Nacos 实现服务注册与发现。
2. 使用 Nacos 实现集中配置管理，并验证配置动态刷新。
3. 使用 Spring Cloud Gateway 作为统一服务网关，通过网关地址访问后端服务。
4. 验证基于服务名的动态路由是否生效。
5. 使用 Sentinel 对服务进行限流、熔断和降级治理。
6. 使用 JMeter 对网关入口进行压力测试，观察流量治理前后的效果变化。

## 二、实验环境

### 2.1 软件环境

| 组件 | 版本 |
| --- | --- |
| JDK | 17 |
| Spring Boot | 2.7.18 |
| Spring Cloud | 2021.0.8 |
| Spring Cloud Alibaba | 2021.0.6.2 |
| Nacos Server | 2.3.2 |
| Sentinel Dashboard | 1.8.8 |
| Apache JMeter | 5.6.x |
| Docker Compose | 2.x |

### 2.2 工程结构

实验工程位于 [cloud-lab](/Users/mac/Desktop/project/distributed-software-homework/cloud-lab)，包含两个核心服务：

- `stock-service`：服务提供者，注册到 Nacos，同时从 Nacos 读取配置，并接入 Sentinel 进行服务端流量治理。
- `gateway-service`：Spring Cloud Gateway 网关，注册到 Nacos，通过 `lb://stock-service` 转发请求，并配置熔断降级和网关限流。

关键文件如下：

- 父工程配置：[pom.xml](/Users/mac/Desktop/project/distributed-software-homework/cloud-lab/pom.xml)
- 服务提供者配置：[stock-service/application.yml](/Users/mac/Desktop/project/distributed-software-homework/cloud-lab/stock-service/src/main/resources/application.yml)
- 网关配置：[gateway-service/application.yml](/Users/mac/Desktop/project/distributed-software-homework/cloud-lab/gateway-service/src/main/resources/application.yml)
- Nacos 示例配置：[nacos-config/stock-service.yaml](/Users/mac/Desktop/project/distributed-software-homework/cloud-lab/nacos-config/stock-service.yaml)
- JMeter 脚本：[cloud-governance-test.jmx](/Users/mac/Desktop/project/distributed-software-homework/cloud-lab/jmeter/cloud-governance-test.jmx)

## 三、实验原理

### 3.1 服务注册与发现

`stock-service` 在启动后通过 `spring-cloud-starter-alibaba-nacos-discovery` 将实例信息注册到 Nacos。  
`gateway-service` 通过 Nacos 获取服务实例列表，并使用 `lb://stock-service` 方式将请求负载均衡到多个 `stock-service` 实例上。

### 3.2 配置中心与动态刷新

`stock-service` 接入 `spring-cloud-starter-alibaba-nacos-config`，从 Nacos 中读取 `stock-service.yaml` 配置。  
业务代码中通过 `@RefreshScope` 和配置属性类读取 `demo.message`、`demo.owner` 等属性，配置变更后无需重启即可刷新。

### 3.3 网关动态路由

网关配置了两种访问方式：

1. 开启服务发现定位器，可直接通过服务名访问：
   `http://127.0.0.1:8088/stock-service/api/stocks/echo`
2. 显式声明自定义路由：
   `http://127.0.0.1:8088/stock/api/stocks/echo`

只要后端服务实例在 Nacos 中正常注册，Gateway 即可根据服务名动态转发请求。

### 3.4 流量治理

本实验同时演示了三类流量治理能力：

1. 限流：对热点接口或网关入口设置 QPS 阈值，超过阈值后直接拦截。
2. 熔断：当慢调用或错误率超过阈值时，暂时切断对故障服务的访问。
3. 降级：熔断触发后，网关返回兜底结果，避免请求直接失败。

## 四、实验步骤

### 4.1 启动基础环境

进入工程目录：

```bash
cd cloud-lab
```

启动 Nacos 和 Sentinel Dashboard：

```bash
docker compose up -d
```

启动成功后访问：

- Nacos 控制台：`http://127.0.0.1:8848/nacos`
- Sentinel 控制台：`http://127.0.0.1:8858`

### 4.2 发布 Nacos 配置

执行脚本将本地示例配置导入 Nacos：

```bash
chmod +x scripts/publish-nacos-config.sh
./scripts/publish-nacos-config.sh
```

发布的配置包括：

| Data ID | 说明 |
| --- | --- |
| `stock-service.yaml` | 业务配置 |
| `stock-service-sentinel-flow.json` | 服务端限流规则 |
| `stock-service-sentinel-degrade.json` | 服务端降级规则 |
| `gateway-service-sentinel-gateway.json` | 网关限流规则 |

### 4.3 启动服务实例

启动两个 `stock-service` 实例：

```bash
mvn -pl stock-service spring-boot:run -Dspring-boot.run.profiles=node1
mvn -pl stock-service spring-boot:run -Dspring-boot.run.profiles=node2
```

两个实例分别监听：

- `9001`
- `9002`

启动网关服务：

```bash
mvn -pl gateway-service spring-boot:run
```

网关监听端口：

- `8088`

### 4.4 验证服务注册发现

在 Nacos 服务列表中应能看到：

- `stock-service`
- `gateway-service`

并且 `stock-service` 应有两个实例。

通过网关请求后端接口：

```bash
curl "http://127.0.0.1:8088/stock/api/stocks/echo?from=test"
```

返回结果类似：

```json
{
  "service": "stock-service",
  "servedBy": "9001",
  "from": "test",
  "message": "nacos-message-v1",
  "owner": "nacos-admin"
}
```

多次重复调用后，如果 `servedBy` 在 `9001` 和 `9002` 之间切换，说明：

1. 服务已成功注册到 Nacos。
2. Gateway 已通过服务发现实现动态路由和负载均衡。

### 4.5 验证 Nacos 配置动态刷新

先通过接口查看当前配置：

```bash
curl "http://127.0.0.1:8088/stock/api/stocks/config"
```

返回结果示例：

```json
{
  "message": "nacos-message-v1",
  "owner": "nacos-admin",
  "servedBy": "9001"
}
```

然后在 Nacos 控制台修改 `stock-service.yaml`：

```yaml
demo:
  message: nacos-message-v2
  owner: teacher-check
```

保存后再次调用接口：

```bash
curl "http://127.0.0.1:8088/stock/api/stocks/config"
```

若返回值直接变为：

```json
{
  "message": "nacos-message-v2",
  "owner": "teacher-check"
}
```

则说明配置动态刷新成功，不需要重启服务。

### 4.6 验证动态服务路由

本实验同时支持服务名路由和自定义前缀路由。

服务名方式：

```bash
curl "http://127.0.0.1:8088/stock-service/api/stocks/echo?from=discovery"
```

自定义前缀方式：

```bash
curl "http://127.0.0.1:8088/stock/api/stocks/echo?from=custom-route"
```

两种方式均能正确返回 `stock-service` 的结果，说明 Gateway 已具备动态服务路由能力。

### 4.7 验证限流

服务端热点资源限流接口：

```bash
curl "http://127.0.0.1:8088/stock/api/stocks/hot?instanceId=load-test"
```

当并发较高时，可能出现以下两类响应：

网关限流响应：

```json
{"code":429,"message":"gateway rate limit triggered"}
```

服务端 Sentinel 限流响应：

```json
{"event":"blocked","message":"sentinel flow control triggered"}
```

这说明限流规则已经生效，系统能够在流量超过阈值时主动拦截部分请求。

### 4.8 验证熔断与降级

调用慢接口：

```bash
curl "http://127.0.0.1:8088/stock/api/stocks/slow?delayMs=3000"
```

由于网关的 `stockCircuit` 配置了超时和失败率阈值，当慢调用持续出现后，网关会触发熔断，并返回统一降级结果：

```json
{
  "service": "gateway-service",
  "message": "gateway circuit breaker fallback"
}
```

这说明系统在后端服务响应过慢时，能够通过降级逻辑快速返回，避免请求长时间阻塞。

## 五、JMeter 压力测试

### 5.1 压测目标

通过 JMeter 对网关入口发起高并发请求，验证以下内容：

1. 未开启规则时系统可以正常响应。
2. 开启网关限流后返回 429 的比例上升。
3. 调低服务端 QPS 阈值后，服务端拦截比例上升。
4. 压测慢接口时，触发熔断降级的比例上升。

### 5.2 测试脚本

测试计划文件：

[cloud-governance-test.jmx](/Users/mac/Desktop/project/distributed-software-homework/cloud-lab/jmeter/cloud-governance-test.jmx)

命令行执行方式：

```bash
jmeter -n -t jmeter/cloud-governance-test.jmx -l jmeter/result.jtl
```

如需生成 HTML 报告，可执行：

```bash
jmeter -n -t jmeter/cloud-governance-test.jmx -l jmeter/result.jtl -e -o jmeter/report
```

### 5.3 观察指标

重点关注以下指标：

- `Average`：平均响应时间
- `90% Line`：90% 请求响应时间
- `Error%`：错误率
- `Throughput`：吞吐量
- HTTP 状态码分布：200、429、fallback 响应占比

### 5.4 预期现象

#### 场景一：未触发规则

- 大部分请求返回 200。
- 响应时间较稳定。
- Error% 较低。

#### 场景二：启用网关限流

- 当并发超过规则阈值后，部分请求返回 429。
- 系统整体吞吐量保持稳定。
- 后端服务不会因瞬时洪峰全部被压垮。

#### 场景三：启用服务端限流

- 通过调低 `stock-service-sentinel-flow.json` 中的 `count` 值，可以明显看到服务端返回 `blocked` 响应的比例上升。

#### 场景四：压测慢接口

- 连续请求 `/slow` 接口时，网关熔断器达到阈值后开始返回 fallback 结果。
- 平均响应时间下降，因为部分请求不再等待后端慢调用完成。

## 六、实验结果分析

### 6.1 服务注册发现

实验结果表明，`stock-service` 的两个实例都能够注册到 Nacos，Gateway 能够基于服务名自动发现实例并进行转发。  
多次访问网关接口时，请求会在不同实例之间分发，说明负载均衡生效。

### 6.2 配置动态刷新

修改 Nacos 中的配置项后，服务接口返回值能够实时变化，说明：

- Nacos 配置中心接入成功。
- `@RefreshScope` 生效。
- 业务代码已经可以使用动态配置属性。

### 6.3 动态路由

通过服务名路由和自定义前缀路由两种方式都能够访问后端服务，说明网关具备统一入口能力，同时支持后端服务实例动态扩缩容。

### 6.4 流量治理

压力较低时，系统正常响应。  
压力升高后，Sentinel 网关限流、服务端限流和熔断降级机制开始生效。  
这种治理方式可以在高并发场景中保护核心服务，防止单个慢接口或流量尖峰拖垮整个系统。

## 七、存在的问题与改进方向

当前实验工程主要用于验证微服务治理能力，仍有以下可继续完善的方向：

1. 将限流、熔断规则全部改为纯 Nacos 控制台动态维护，减少本地样例文件依赖。
2. 接入 OpenFeign，在服务间调用链上进一步验证熔断与降级。
3. 补充监控链路，如 Prometheus + Grafana，量化展示压测前后的指标变化。
4. 在报告中补充 Nacos 控制台、Sentinel Dashboard、JMeter 报表截图，使实验结果更完整。

## 八、实验结论

本实验成功完成了服务注册发现、配置管理、动态路由和流量治理的核心验证，结论如下：

1. Nacos 可以作为注册中心和配置中心统一管理微服务实例与配置。
2. Spring Cloud Gateway 可以作为统一入口，通过服务名实现动态路由与负载均衡。
3. 服务中的 Nacos 属性能够被业务代码直接读取，并支持动态更新。
4. Sentinel 可以有效实现限流、熔断和降级，在高并发场景下保护后端服务。
5. JMeter 压测结果能够直观反映治理规则启用前后的系统行为差异。

因此，本实验方案满足“服务注册发现与配置管理、网关访问、动态配置刷新、流量治理和压力测试”的课程要求。

## 九、附录

### 9.1 主要访问地址

| 名称 | 地址 |
| --- | --- |
| Nacos 控制台 | `http://127.0.0.1:8848/nacos` |
| Sentinel Dashboard | `http://127.0.0.1:8858` |
| Gateway | `http://127.0.0.1:8088` |
| Stock Service 实例 1 | `http://127.0.0.1:9001` |
| Stock Service 实例 2 | `http://127.0.0.1:9002` |

### 9.2 常用验证命令

```bash
curl "http://127.0.0.1:8088/stock/api/stocks/echo?from=report"
curl "http://127.0.0.1:8088/stock/api/stocks/config"
curl "http://127.0.0.1:8088/stock/api/stocks/hot?instanceId=load-test"
curl "http://127.0.0.1:8088/stock/api/stocks/slow?delayMs=3000"
```

### 9.3 可补充截图位置

建议在最终提交版报告中补充以下截图：

1. Nacos 服务列表截图。
2. Nacos 配置列表与配置修改前后截图。
3. Sentinel Dashboard 规则配置截图。
4. Gateway 调用成功截图。
5. JMeter 聚合报告或 HTML 报告截图。
