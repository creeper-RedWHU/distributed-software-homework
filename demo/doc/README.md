# 商品库存与秒杀系统 - 设计文档

> 日期：2026/03/04
> 更新：2026/03/07 - 新增部署方案文档

## 文档目录

### 系统设计篇

| # | 文档 | 内容 |
|---|------|------|
| 01 | [系统概述与架构设计](01-系统概述与架构设计.md) | 技术栈、分层架构、部署拓扑、模块划分 |
| 02 | [数据库设计](02-数据库设计.md) | ER图、表结构DDL、MyBatis Mapper、索引策略、Redis数据结构 |
| 03 | [功能模块设计](03-功能模块设计.md) | 用例图、秒杀主流程、订单状态机、库存管理、Lua扣减脚本 |
| 04 | [高并发解决方案](04-高并发解决方案.md) | 漏斗模型、分层限流、Redis原子扣减、Kafka削峰、乐观锁、分布式锁 |
| 05 | [通讯与扩展机制](05-通讯与扩展机制.md) | Event事件总线、Hook插件化、Kafka MQ、WebSocket实时推送 |
| 06 | [API接口设计](06-API接口设计.md) | RESTful API规范、错误码、接口详细定义、WebSocket消息协议 |
| 07 | [页面原型设计](07-页面原型设计.md) | 页面结构、Salt原型图、交互流程、前端技术方案 |

### 部署运维篇

| # | 文档 | 内容 |
|---|------|------|
| 08 | [Docker部署指南](08-Docker部署指南.md) | Docker详细部署、故障排查、性能优化、备份恢复 |
| 09 | [Docker快速开始](09-Docker快速开始.md) | Docker一键部署、快速测试、常用命令 |
| 10 | [Kubernetes集群部署](10-Kubernetes集群部署.md) | K8s完整部署方案、自动伸缩、滚动更新、监控日志 |
| 11 | [Docker Swarm集群部署](11-Docker-Swarm集群部署.md) | Swarm集群搭建、服务编排、滚动更新、节点管理 |
| 12 | [集群部署方案对比](12-集群部署方案对比.md) | Compose vs Swarm vs K8s、选型建议、迁移路径 |

## 技术栈

- **后端**: Spring Boot 4.0 + MyBatis + MySQL
- **缓存**: Redis (库存预扣减、分布式锁、限流)
- **消息队列**: Apache Kafka (异步下单、流量削峰)
- **实时推送**: WebSocket (秒杀结果、库存变化)
- **扩展机制**: Event事件总线 + Hook插件化
- **容器化**: Docker + Docker Compose
- **集群编排**: Docker Swarm / Kubernetes

## 核心设计理念

```
高并发 → 分层过滤漏斗模型（Nginx → 应用层 → Redis → Kafka → MySQL）
高可用 → 中间件集群 + 服务无状态 + 多实例部署
快响应 → Redis内存操作(<1ms) + 异步下单 + WebSocket推送
强一致 → Redis Lua原子脚本 + MySQL乐观锁 + 唯一索引 + 定时对账
```

## 快速开始

### Docker 单机部署（开发/测试）

```bash
# 一键启动所有服务
./docker-start.sh

# 或使用 docker-compose
docker-compose up -d

# 访问应用
curl http://localhost:8080/actuator/health
```

### Docker Swarm 集群部署（生产）

```bash
# 1. 初始化 Swarm 集群
cd swarm
./swarm-init.sh

# 2. 部署应用栈
./swarm-deploy.sh

# 3. 查看服务状态
docker service ls
```

### Kubernetes 集群部署（大规模生产）

```bash
# 1. 部署到 K8s 集群
cd k8s
./deploy.sh

# 2. 查看 Pod 状态
kubectl get pods -n seckill

# 3. 查看服务
kubectl get svc -n seckill
```

详细部署说明请参考 [部署运维篇](#部署运维篇) 相关文档。

## 项目结构

```
demo/
├── src/                           # 源代码
│   ├── main/
│   │   ├── java/                  # Java 代码
│   │   └── resources/             # 配置文件
│   └── test/                      # 测试代码
├── doc/                           # 文档
│   ├── 01-系统概述与架构设计.md
│   ├── 02-数据库设计.md
│   ├── ...
│   └── 12-集群部署方案对比.md
├── k8s/                           # Kubernetes 配置
│   ├── namespace.yaml
│   ├── configmap.yaml
│   ├── app-deployment.yaml
│   └── deploy.sh
├── swarm/                         # Docker Swarm 配置
│   ├── docker-compose.swarm.yml
│   ├── swarm-init.sh
│   └── swarm-deploy.sh
├── docker/                        # Docker 配置
│   └── nginx/
│       ├── nginx.conf
│       └── conf.d/
├── Dockerfile                     # Docker 镜像构建
├── docker-compose.yml             # Docker Compose 编排
├── docker-start.sh                # 一键启动脚本
└── pom.xml                        # Maven 配置
```
