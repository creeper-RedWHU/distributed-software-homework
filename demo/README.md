# 商品库存与秒杀系统

一个基于 Spring Boot 的高并发秒杀系统，支持多种部署方案（Docker Compose / Docker Swarm / Kubernetes）。

## 项目特点

- **高并发**: 支持 10 万+ QPS，分层限流漏斗模型
- **高可用**: 中间件集群 + 服务多副本 + 自动故障转移
- **快响应**: Redis 内存操作(<1ms) + 异步下单 + WebSocket 实时推送
- **强一致**: Redis Lua 原子脚本 + MySQL 乐观锁 + 定时对账
- **易部署**: 支持 Docker 一键部署、Swarm 集群、K8s 编排

## 技术栈

| 分层 | 技术 | 用途 |
|------|------|------|
| **后端框架** | Spring Boot 4.0 | REST API、依赖注入 |
| **ORM** | MyBatis 3.0 | 数据库操作 |
| **数据库** | MySQL 8.0 | 数据持久化、主从复制 |
| **缓存** | Redis 7 | 库存预扣减、分布式锁、限流 |
| **消息队列** | Kafka 3.5 | 异步下单、流量削峰、事件通知 |
| **实时推送** | WebSocket | 秒杀结果、库存变化实时通知 |
| **反向代理** | Nginx | 负载均衡、限流、静态资源 |
| **容器化** | Docker | 应用容器化 |
| **编排工具** | Docker Compose / Swarm / K8s | 服务编排、集群管理 |

## 快速开始

### 前置要求

- Docker 20.10+
- Docker Compose 2.0+
- 4GB+ 可用内存
- 10GB+ 可用磁盘

### 一键启动（推荐）

```bash
# 克隆项目
git clone <repository-url>
cd demo

# 启动所有服务
./docker-start.sh

# 等待服务就绪后访问
curl http://localhost:8080/actuator/health
```

**就是这么简单！** 5 分钟内完成部署。

### 手动启动

```bash
# 使用 Docker Compose
docker-compose up -d

# 查看服务状态
docker-compose ps

# 查看应用日志
docker-compose logs -f app
```

## 部署方案

本项目支持三种部署方案，适用于不同场景：

### 1. Docker Compose（开发/测试）

**适用场景**: 本地开发、功能测试、快速演示

```bash
# 启动
docker-compose up -d

# 访问
http://localhost:8080
```

**特点**:
- ✅ 简单快速，5 分钟启动
- ✅ 资源占用低
- ✅ 开发体验好
- ❌ 单机部署，无高可用

**详细文档**: [Docker 快速开始](doc/09-Docker快速开始.md)

---

### 2. Docker Swarm（中小规模生产）

**适用场景**: 中小型项目、10-50 节点集群、快速上线

```bash
# 1. 初始化集群
cd swarm
./swarm-init.sh

# 2. 部署应用
./swarm-deploy.sh

# 3. 扩容
docker service scale seckill_app=5
```

**特点**:
- ✅ 原生集成，学习成本低
- ✅ 内置负载均衡和服务发现
- ✅ 支持滚动更新和回滚
- ✅ 30 分钟搭建集群
- ⚠️ 适合中小规模（<100 节点）

**详细文档**: [Docker Swarm 集群部署](doc/11-Docker-Swarm集群部署.md)

---

### 3. Kubernetes（大规模生产）

**适用场景**: 大型项目、微服务架构、50+ 节点、需要自动伸缩

```bash
# 1. 部署到 K8s
cd k8s
./deploy.sh

# 2. 查看状态
kubectl get all -n seckill

# 3. 自动伸缩
kubectl autoscale deployment seckill-app \
  --min=3 --max=10 --cpu-percent=70 -n seckill
```

**特点**:
- ✅✅ 功能最强大，生态最丰富
- ✅✅ 自动伸缩（HPA/VPA）
- ✅✅ 完善的监控和日志
- ✅✅ 支持多云部署
- ❌ 学习曲线陡峭
- ❌ 运维成本高

**详细文档**: [Kubernetes 集群部署](doc/10-Kubernetes集群部署.md)

---

### 方案对比

| 维度 | Docker Compose | Docker Swarm | Kubernetes |
|------|---------------|--------------|------------|
| **复杂度** | ⭐ 简单 | ⭐⭐ 中等 | ⭐⭐⭐⭐⭐ 复杂 |
| **部署时间** | 5分钟 | 30分钟 | 2-4小时 |
| **集群规模** | 单机 | 10-100节点 | 5000+节点 |
| **高可用** | ❌ | ✅ | ✅✅ |
| **自动伸缩** | ❌ | ⚠️ 手动 | ✅✅ 自动 |
| **适用场景** | 开发测试 | 中小项目 | 大型生产 |

**选型建议**: [集群部署方案对比](doc/12-集群部署方案对比.md)

## 架构设计

### 系统架构

```
┌─────────────────────────────────────────────────────┐
│                    客户端 (Browser/App)               │
├─────────────────────────────────────────────────────┤
│              Nginx 负载均衡 + 限流                     │
├─────────────────────────────────────────────────────┤
│                  Spring Boot 应用集群                  │
│  ┌───────────┬───────────┬───────────┬────────────┐ │
│  │ Controller│   Event   │   Hook    │  WebSocket │ │
│  │   REST    │   Bus     │  Plugin   │   Push     │ │
│  └─────┬─────┴─────┬─────┴─────┬─────┴──────┬─────┘ │
│  ┌─────┴───────────┴───────────┴────────────┴─────┐ │
│  │              Service 业务逻辑层                   │ │
│  └─────┬──────────────┬───────────────────┬───────┘ │
│  ┌─────┴─────┐  ┌─────┴─────┐  ┌─────────┴───────┐ │
│  │   Redis   │  │   Kafka   │  │  MyBatis+MySQL  │ │
│  │  缓存层   │  │  消息队列  │  │    持久层        │ │
│  └───────────┘  └───────────┘  └─────────────────┘  │
└─────────────────────────────────────────────────────┘
```

### 秒杀流程

```
用户请求 (100,000 QPS)
    │
    ▼
Nginx 限流 (50%)  ──→ 50,000 QPS
    │
    ▼
应用层限流 + Hook 插件  ──→ 10,000 QPS
    │
    ▼
Redis 库存预扣减 (Lua 原子脚本)  ──→ 100 有效请求
    │
    ▼
Kafka 异步下单 (削峰填谷)  ──→ 100 订单消息
    │
    ▼
MySQL 持久化 (乐观锁 + 唯一索引)  ──→ 100 订单记录
    │
    ▼
WebSocket 推送结果  ──→ 用户实时收到通知
```

## 核心功能

- **秒杀活动管理**: 创建、查询、状态管理
- **库存管理**: Redis 预扣减、MySQL 持久化、定时对账
- **订单管理**: 异步下单、订单超时取消、库存回滚
- **实时推送**: WebSocket 推送秒杀结果和库存变化
- **Hook 插件化**: 限流、日志、重复订单检测等可扩展插件
- **Event 事件总线**: 解耦业务逻辑，支持事件驱动
- **🔐 用户认证与权限管理**: 基于 Apache Shiro 的登录认证和权限控制（新增）

## 接口示例

### 用户登录（新增）

```bash
# 商家登录
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -c cookies.txt \
  -d '{"username":"merchant1","password":"123456"}'

# 获取当前用户信息
curl http://localhost:8080/api/auth/info -b cookies.txt

# 测试权限
curl http://localhost:8080/api/auth/test/merchant -b cookies.txt
```

**测试账号**: merchant1/buyer1/admin1 (密码: 123456)

**详细文档**: [doc/Shiro登录系统/](doc/Shiro登录系统/) | [完整文档索引](doc/README.md)

### 查询秒杀活动列表

```bash
curl http://localhost:8080/api/seckill/activities
```

### 参与秒杀

```bash
curl -X POST http://localhost:8080/api/seckill/1/participate \
  -H "Content-Type: application/json" \
  -d '{"userId": 1}'
```

### 查询订单

```bash
curl http://localhost:8080/api/orders/user/1
```

### WebSocket 连接

```javascript
const ws = new WebSocket('ws://localhost:8080/ws/seckill?userId=1');
ws.onmessage = (event) => {
    console.log('收到推送:', event.data);
};
```

## 文档

### 完整文档列表

#### 🔐 用户认证与权限管理（新增）

**📂 所有文档已整理到 [doc/](doc/) 目录**

| 分类 | 说明 |
|------|------|
| [📘 Shiro登录系统](doc/Shiro登录系统/) | 快速开始、使用指南、技术文档 |
| [🔧 问题修复](doc/问题修复/) | 所有问题的排查和解决方案 |
| [🧪 测试工具](doc/测试工具/) | API 测试脚本、Postman 集合 |
| [📑 完整索引](doc/README.md) | ⭐ 文档导航和快速查找 |

**推荐阅读顺序**:
1. [快速开始](doc/Shiro登录系统/01-快速开始.md)
2. [SpringBoot版本说明](doc/问题修复/04-SpringBoot版本降级说明.md)（重要）
3. [详细使用指南](doc/Shiro登录系统/02-详细使用指南.md)

#### 📘 秒杀系统文档

| 文档 | 说明 |
|------|------|
| [系统概述与架构设计](doc/01-系统概述与架构设计.md) | 技术栈、分层架构、模块划分 |
| [数据库设计](doc/02-数据库设计.md) | 表结构、索引策略、Redis 数据结构 |
| [功能模块设计](doc/03-功能模块设计.md) | 秒杀流程、订单状态机、库存管理 |
| [高并发解决方案](doc/04-高并发解决方案.md) | 限流、缓存、异步、锁机制 |
| [通讯与扩展机制](doc/05-通讯与扩展机制.md) | Event、Hook、Kafka、WebSocket |
| [API接口设计](doc/06-API接口设计.md) | RESTful API、错误码、接口定义 |
| [页面原型设计](doc/07-页面原型设计.md) | 页面结构、交互流程 |
| [Docker部署指南](doc/08-Docker部署指南.md) | Docker 详细部署、故障排查 |
| [Docker快速开始](doc/09-Docker快速开始.md) | Docker 一键部署、快速测试 |
| [Kubernetes集群部署](doc/10-Kubernetes集群部署.md) | K8s 完整部署方案、监控日志 |
| [Docker Swarm集群部署](doc/11-Docker-Swarm集群部署.md) | Swarm 集群搭建、服务编排 |
| [集群部署方案对比](doc/12-集群部署方案对比.md) | 三种方案对比、选型建议 |

## 项目结构

```
demo/
├── doc/                           # 📚 文档
│   ├── 01-系统概述与架构设计.md
│   ├── 02-数据库设计.md
│   ├── ...
│   └── 12-集群部署方案对比.md
├── k8s/                           # ☸️ Kubernetes 配置
│   ├── namespace.yaml
│   ├── app-deployment.yaml
│   ├── mysql-statefulset.yaml
│   ├── ingress.yaml
│   ├── hpa.yaml
│   └── deploy.sh                  # K8s 部署脚本
├── swarm/                         # 🐳 Docker Swarm 配置
│   ├── docker-compose.swarm.yml
│   ├── swarm-init.sh              # Swarm 初始化脚本
│   └── swarm-deploy.sh            # Swarm 部署脚本
├── docker/                        # 🐋 Docker 配置
│   └── nginx/
│       ├── nginx.conf
│       └── conf.d/seckill.conf
├── src/                           # 💻 源代码
│   ├── main/
│   │   ├── java/com/example/demo/
│   │   │   ├── controller/       # REST API
│   │   │   ├── service/          # 业务逻辑
│   │   │   ├── mapper/           # MyBatis Mapper
│   │   │   ├── model/            # 数据模型
│   │   │   ├── event/            # 事件总线
│   │   │   ├── hook/             # Hook 插件
│   │   │   ├── mq/               # Kafka 消息
│   │   │   └── websocket/        # WebSocket
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-docker.yml
│   │       ├── sql/schema.sql
│   │       └── lua/*.lua
│   └── test/                      # 测试代码
├── Dockerfile                     # Docker 镜像构建
├── docker-compose.yml             # Docker Compose 编排
├── docker-start.sh                # 🚀 一键启动脚本
├── pom.xml                        # Maven 配置
└── README.md                      # 项目说明
```

## 常用命令

### Docker Compose

```bash
# 启动
./docker-start.sh
# 或
docker-compose up -d

# 查看日志
docker-compose logs -f app

# 扩容（同一节点）
docker-compose up -d --scale app=3

# 停止
docker-compose down

# 清理数据
docker-compose down -v
```

### Docker Swarm

```bash
# 初始化集群
cd swarm && ./swarm-init.sh

# 部署应用
./swarm-deploy.sh

# 查看服务
docker service ls

# 扩容
docker service scale seckill_app=5

# 更新镜像
./swarm-deploy.sh update app new-image:tag

# 查看日志
docker service logs -f seckill_app

# 删除栈
docker stack rm seckill
```

### Kubernetes

```bash
# 部署
cd k8s && ./deploy.sh

# 查看资源
kubectl get all -n seckill

# 查看日志
kubectl logs -f deployment/seckill-app -n seckill

# 扩容
kubectl scale deployment/seckill-app --replicas=5 -n seckill

# 滚动更新
kubectl set image deployment/seckill-app seckill-app=new-image:tag -n seckill

# 查看 HPA
kubectl get hpa -n seckill

# 删除
./deploy.sh cleanup
```

## 监控与运维

### 健康检查

```bash
# 应用健康状态
curl http://localhost:8080/actuator/health

# 查看指标
curl http://localhost:8080/actuator/metrics
```

### 日志查看

```bash
# Docker Compose
docker-compose logs -f app

# Docker Swarm
docker service logs -f seckill_app

# Kubernetes
kubectl logs -f deployment/seckill-app -n seckill
```

### 性能测试

```bash
# 使用 Apache Bench
ab -n 10000 -c 100 http://localhost:8080/api/seckill/activities

# 使用 wrk
wrk -t4 -c100 -d30s http://localhost:8080/api/seckill/activities
```

## 故障排查

详细的故障排查步骤请参考：
- [Docker 部署指南 - 故障排查](doc/08-Docker部署指南.md#故障排查)
- [Kubernetes 部署 - 故障排查](doc/10-Kubernetes集群部署.md#故障排查)
- [Docker Swarm 部署 - 故障排查](doc/11-Docker-Swarm集群部署.md#故障排查)

## 贡献指南

欢迎提交 Issue 和 Pull Request！

## 许可证

[MIT License](LICENSE)

## 联系方式

- 项目文档: [doc/README.md](doc/README.md)
- 技术支持: 请提交 Issue

---

**开始使用**: `./docker-start.sh` 🚀
