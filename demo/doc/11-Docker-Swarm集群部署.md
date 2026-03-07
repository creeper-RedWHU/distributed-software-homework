# Docker Swarm 集群部署指南

## 目录

- [概述](#概述)
- [架构设计](#架构设计)
- [前置要求](#前置要求)
- [快速开始](#快速开始)
- [详细部署步骤](#详细部署步骤)
- [服务管理](#服务管理)
- [滚动更新](#滚动更新)
- [监控与日志](#监控与日志)
- [故障排查](#故障排查)
- [最佳实践](#最佳实践)

## 概述

Docker Swarm 是 Docker 原生的集群管理和编排工具，提供简单易用的容器编排功能。相比 Kubernetes，Swarm 更轻量级，学习曲线更平缓，适合中小型项目。

### Docker Swarm 优势

- **简单易用**: 命令简单，学习曲线平缓
- **原生集成**: Docker 内置，无需额外安装
- **快速部署**: 几分钟内搭建集群
- **内置负载均衡**: 自动负载均衡和服务发现
- **滚动更新**: 支持零停机更新
- **高可用**: 多管理节点支持
- **安全性**: 内置 TLS 加密和密钥管理

## 架构设计

### 集群拓扑

```
┌─────────────────────────────────────────────────────────────┐
│                    Docker Swarm Cluster                      │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌──────────────────────────────────────────────────────┐   │
│  │                   Manager Nodes                       │   │
│  │  ┌────────────┐  ┌────────────┐  ┌────────────┐     │   │
│  │  │ Manager 1  │  │ Manager 2  │  │ Manager 3  │     │   │
│  │  │  (Leader)  │  │ (Reachable)│  │ (Reachable)│     │   │
│  │  └────────────┘  └────────────┘  └────────────┘     │   │
│  │         Raft 共识算法（奇数个管理节点）               │   │
│  └──────────────────────────────────────────────────────┘   │
│                           │                                   │
│  ┌────────────────────────┴─────────────────────────────┐   │
│  │                   Worker Nodes                        │   │
│  │  ┌────────────┐  ┌────────────┐  ┌────────────┐     │   │
│  │  │  Worker 1  │  │  Worker 2  │  │  Worker 3  │     │   │
│  │  │            │  │            │  │            │     │   │
│  │  │ App Tasks  │  │ App Tasks  │  │ App Tasks  │     │   │
│  │  │ Redis      │  │ Kafka      │  │ MySQL      │     │   │
│  │  └────────────┘  └────────────┘  └────────────┘     │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                               │
│  ┌──────────────────────────────────────────────────────┐   │
│  │          Overlay Network (Encrypted)                  │   │
│  │        服务发现 + 内置负载均衡 + 路由网格            │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                               │
│  ┌──────────────────────────────────────────────────────┐   │
│  │       Persistent Volumes (NFS/GlusterFS)             │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### 服务分布

```
┌─────────────────────────────────────────────────────────┐
│                    Ingress Network                       │
│                  (外部访问入口)                          │
│              Ports: 80, 443, 8080                        │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────┴────────────────────────────────────┐
│                  Routing Mesh                            │
│              (负载均衡到所有节点)                        │
└────────────────────┬────────────────────────────────────┘
                     │
     ┌───────────────┼───────────────┐
     │               │               │
┌────▼─────┐   ┌────▼─────┐   ┌────▼─────┐
│ nginx    │   │ app      │   │ app      │   Application
│ (proxy)  │   │ (task 1) │   │ (task 2) │   Layer
│ 2 tasks  │   │          │   │          │
└──────────┘   └────┬─────┘   └────┬─────┘
                    │              │
     ┌──────────────┴──────┬───────┴──────┐
     │                     │              │
┌────▼─────┐   ┌──────────▼──┐   ┌───────▼────┐
│ mysql    │   │ redis       │   │ kafka      │  Storage
│ (master) │   │ (cluster)   │   │ (cluster)  │  Layer
│ 1 task   │   │ 3 tasks     │   │ 3 tasks    │
└──────────┘   └─────────────┘   └────────────┘
```

## 前置要求

### 硬件要求

| 节点类型 | 最小配置 | 推荐配置 | 数量 |
|---------|---------|---------|------|
| Manager | 2 Core, 4GB RAM | 4 Core, 8GB RAM | 3（奇数） |
| Worker | 2 Core, 4GB RAM | 4 Core, 8GB RAM | 3+ |

### 软件要求

- Docker Engine 20.10+
- Linux 操作系统（推荐 Ubuntu 20.04+）
- 开放端口：
  - 2377/tcp: 集群管理通信
  - 7946/tcp+udp: 节点间通信
  - 4789/udp: overlay 网络流量
  - 80/tcp, 443/tcp: 应用访问
  - 8080/tcp: 应用端口

### 网络要求

- 所有节点可以互相访问
- 网络延迟 < 10ms（推荐）
- 稳定的网络连接

## 快速开始

### 一键初始化集群

```bash
# 在管理节点执行
cd /Users/mac/Desktop/project/distributed-software-homework/demo/swarm

# 初始化 Swarm 集群
./swarm-init.sh

# 部署所有服务
./swarm-deploy.sh

# 查看服务状态
docker service ls

# 查看应用副本
docker service ps seckill_app
```

## 详细部署步骤

### 步骤 1: 初始化 Swarm 集群

#### 在第一个管理节点上初始化

```bash
# 初始化 Swarm（指定管理节点 IP）
docker swarm init --advertise-addr <MANAGER-IP>

# 输出示例：
# Swarm initialized: current node (xxxx) is now a manager.
# To add a worker to this swarm, run the following command:
#     docker swarm join --token SWMTKN-1-xxx <MANAGER-IP>:2377
# To add a manager to this swarm, run:
#     docker swarm join-token manager
```

#### 添加更多管理节点（推荐 3 个）

```bash
# 在第一个管理节点上获取管理节点 token
docker swarm join-token manager

# 在其他管理节点上执行输出的命令
docker swarm join --token SWMTKN-1-xxx <MANAGER-IP>:2377
```

#### 添加工作节点

```bash
# 在第一个管理节点上获取工作节点 token
docker swarm join-token worker

# 在工作节点上执行输出的命令
docker swarm join --token SWMTKN-1-xxx <MANAGER-IP>:2377
```

#### 验证集群

```bash
# 查看节点列表
docker node ls

# 输出示例：
# ID              HOSTNAME   STATUS   AVAILABILITY   MANAGER STATUS
# xxx *           manager1   Ready    Active         Leader
# xxx             manager2   Ready    Active         Reachable
# xxx             manager3   Ready    Active         Reachable
# xxx             worker1    Ready    Active
# xxx             worker2    Ready    Active
# xxx             worker3    Ready    Active
```

### 步骤 2: 创建网络和卷

```bash
# 创建 overlay 网络（加密）
docker network create \
  --driver overlay \
  --attachable \
  --opt encrypted \
  seckill-network

# 验证网络
docker network ls | grep seckill
```

### 步骤 3: 创建 Secrets 和 Configs

```bash
# 创建 MySQL 密码 Secret
echo "your_root_password" | docker secret create mysql_root_password -
echo "your_user_password" | docker secret create mysql_user_password -

# 创建 Redis 密码 Secret
echo "your_redis_password" | docker secret create redis_password -

# 创建应用配置 Config
docker config create app_config src/main/resources/application-docker.yml

# 查看 Secrets 和 Configs
docker secret ls
docker config ls
```

### 步骤 4: 部署服务栈

```bash
# 使用 docker-compose.swarm.yml 部署
docker stack deploy -c swarm/docker-compose.swarm.yml seckill

# 查看栈状态
docker stack ls
docker stack ps seckill

# 查看服务列表
docker stack services seckill
```

### 步骤 5: 验证部署

```bash
# 查看所有服务
docker service ls

# 查看应用服务详情
docker service ps seckill_app

# 查看服务日志
docker service logs -f seckill_app

# 测试应用访问
curl http://localhost:8080/actuator/health
```

## 服务管理

### 查看服务

```bash
# 列出所有服务
docker service ls

# 查看服务详情
docker service inspect seckill_app

# 查看服务任务（副本）
docker service ps seckill_app

# 查看服务日志
docker service logs -f seckill_app --tail 100

# 查看服务配置
docker service inspect seckill_app --pretty
```

### 扩缩容服务

```bash
# 扩容应用到 5 个副本
docker service scale seckill_app=5

# 缩容到 2 个副本
docker service scale seckill_app=2

# 同时扩缩多个服务
docker service scale seckill_app=5 seckill_redis=3
```

### 更新服务

```bash
# 更新镜像版本
docker service update \
  --image your-registry.com/seckill-app:v1.1.0 \
  seckill_app

# 更新环境变量
docker service update \
  --env-add JAVA_OPTS="-Xmx2g" \
  seckill_app

# 更新副本数
docker service update \
  --replicas 5 \
  seckill_app

# 更新资源限制
docker service update \
  --limit-cpu 2 \
  --limit-memory 2g \
  seckill_app
```

### 删除服务

```bash
# 删除单个服务
docker service rm seckill_app

# 删除整个栈
docker stack rm seckill
```

## 滚动更新

### 配置滚动更新策略

```yaml
deploy:
  update_config:
    parallelism: 2        # 每次更新 2 个任务
    delay: 10s            # 每批次间隔 10 秒
    failure_action: rollback  # 失败时自动回滚
    monitor: 60s          # 监控 60 秒
    max_failure_ratio: 0.3    # 最大失败率 30%
    order: stop-first     # 先停止旧任务再启动新任务
```

### 执行滚动更新

```bash
# 方式1: 使用 service update
docker service update \
  --image your-registry.com/seckill-app:v1.1.0 \
  --update-parallelism 2 \
  --update-delay 10s \
  seckill_app

# 方式2: 重新部署栈（会应用新配置）
docker stack deploy -c swarm/docker-compose.swarm.yml seckill

# 查看更新进度
docker service ps seckill_app
```

### 回滚服务

```bash
# 回滚到上一个版本
docker service rollback seckill_app

# 查看回滚状态
docker service ps seckill_app
```

## 节点管理

### 节点标签

```bash
# 添加标签
docker node update --label-add type=compute worker1
docker node update --label-add disk=ssd worker2

# 根据标签约束服务部署
docker service update \
  --constraint-add node.labels.type==compute \
  seckill_app
```

### 节点可用性

```bash
# 设置节点为维护模式（停止调度新任务）
docker node update --availability drain worker1

# 恢复节点
docker node update --availability active worker1

# 暂停节点（保持任务但不接受新任务）
docker node update --availability pause worker1
```

### 提升/降级节点

```bash
# 提升 worker 为 manager
docker node promote worker1

# 降级 manager 为 worker
docker node demote manager3
```

## 监控与日志

### 服务监控

```bash
# 实时监控服务状态
watch -n 2 'docker service ls'

# 查看服务事件
docker events --filter type=service

# 查看节点资源使用
docker node ps $(docker node ls -q)
```

### 日志管理

```bash
# 查看服务日志
docker service logs seckill_app

# 实时跟踪日志
docker service logs -f seckill_app

# 查看最近 100 行
docker service logs --tail 100 seckill_app

# 查看最近 1 小时
docker service logs --since 1h seckill_app

# 查看指定任务日志
docker logs <task-container-id>
```

### 集成 Prometheus 监控

```yaml
# 在 docker-compose.swarm.yml 中添加
services:
  prometheus:
    image: prom/prometheus
    ports:
      - "9090:9090"
    volumes:
      - ./swarm/prometheus.yml:/etc/prometheus/prometheus.yml
    deploy:
      placement:
        constraints:
          - node.role == manager

  grafana:
    image: grafana/grafana
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
    deploy:
      placement:
        constraints:
          - node.role == manager
```

### 集成 ELK 日志

```yaml
services:
  elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:7.17.0
    environment:
      - discovery.type=single-node
    deploy:
      resources:
        limits:
          memory: 1g

  logstash:
    image: docker.elastic.co/logstash/logstash:7.17.0
    depends_on:
      - elasticsearch

  kibana:
    image: docker.elastic.co/kibana/kibana:7.17.0
    ports:
      - "5601:5601"
    depends_on:
      - elasticsearch
```

## 故障排查

### 常见问题

#### 1. 服务无法启动

```bash
# 查看服务状态
docker service ps seckill_app --no-trunc

# 查看任务日志
docker service logs seckill_app

# 检查容器日志
docker logs <container-id>

# 常见原因：
# - 镜像拉取失败
# - 端口冲突
# - 资源不足
# - 配置错误
```

#### 2. 网络连接问题

```bash
# 检查网络
docker network ls
docker network inspect seckill-network

# 测试服务连通性
docker run --rm --network seckill-network alpine ping mysql

# 检查 DNS 解析
docker run --rm --network seckill-network alpine nslookup mysql
```

#### 3. 节点离线

```bash
# 查看节点状态
docker node ls

# 查看节点详情
docker node inspect <node-id>

# 重新加入集群
# 在问题节点上：
docker swarm leave
# 在管理节点上获取 token 后重新加入
docker swarm join --token <token> <manager-ip>:2377
```

#### 4. 服务更新失败

```bash
# 查看更新状态
docker service ps seckill_app

# 回滚服务
docker service rollback seckill_app

# 强制更新（不推荐）
docker service update --force seckill_app
```

### 调试命令

```bash
# 进入服务容器
docker exec -it $(docker ps -q -f name=seckill_app) sh

# 查看服务配置
docker service inspect seckill_app --pretty

# 查看任务详情
docker inspect <task-id>

# 查看集群状态
docker info

# 查看 Swarm 配置
docker swarm ca
```

## 最佳实践

### 1. 集群规划

- **管理节点**: 使用 3 或 5 个（奇数），避免脑裂
- **工作节点**: 至少 3 个，支持高可用
- **节点标签**: 使用标签管理节点特性
- **资源规划**: 预留 20% 资源用于故障转移

### 2. 服务设计

- **无状态服务**: 应用设计为无状态，方便扩展
- **健康检查**: 配置 HEALTHCHECK 指令
- **资源限制**: 设置 CPU 和内存限制
- **副本数**: 至少 3 个副本，分布在不同节点

### 3. 网络配置

- **Overlay 网络**: 使用加密的 overlay 网络
- **服务发现**: 利用内置 DNS 服务发现
- **负载均衡**: 使用 Routing Mesh 自动负载均衡
- **端口映射**: 避免端口冲突

### 4. 存储管理

- **持久化存储**: 使用外部存储（NFS/GlusterFS）
- **数据备份**: 定期备份数据
- **卷驱动**: 使用合适的卷驱动（local/nfs/ceph）

### 5. 安全配置

- **TLS 加密**: Swarm 自动启用 TLS
- **Secrets 管理**: 使用 Docker Secrets 存储敏感信息
- **网络隔离**: 使用独立的 overlay 网络
- **最小权限**: 容器以非 root 用户运行

### 6. 更新策略

- **滚动更新**: 配置合理的更新策略
- **健康检查**: 更新时检查健康状态
- **失败回滚**: 配置自动回滚
- **灰度发布**: 先更新部分副本测试

### 7. 监控告警

- **服务监控**: 监控服务状态和性能
- **节点监控**: 监控节点资源使用
- **日志收集**: 集中收集日志
- **告警配置**: 配置关键指标告警

### 8. 灾难恢复

- **定期备份**: 备份 Swarm 配置和数据
- **多可用区**: 跨可用区部署
- **故障演练**: 定期进行故障演练
- **恢复计划**: 准备详细的恢复计划

## 配置示例

### docker-compose.swarm.yml 示例

```yaml
version: '3.8'

services:
  app:
    image: your-registry.com/seckill-app:latest
    networks:
      - seckill-network
    ports:
      - target: 8080
        published: 8080
        mode: host
    environment:
      - SPRING_PROFILES_ACTIVE=docker
    secrets:
      - mysql_root_password
    configs:
      - source: app_config
        target: /app/config/application.yml
    deploy:
      replicas: 3
      update_config:
        parallelism: 1
        delay: 10s
        failure_action: rollback
        monitor: 30s
        order: start-first
      rollback_config:
        parallelism: 1
        delay: 5s
      restart_policy:
        condition: on-failure
        delay: 5s
        max_attempts: 3
        window: 120s
      resources:
        limits:
          cpus: '2'
          memory: 2G
        reservations:
          cpus: '0.5'
          memory: 1G
      placement:
        max_replicas_per_node: 1
        constraints:
          - node.role == worker
        preferences:
          - spread: node.labels.zone

networks:
  seckill-network:
    driver: overlay
    attachable: true
    driver_opts:
      encrypted: "true"

secrets:
  mysql_root_password:
    external: true

configs:
  app_config:
    external: true
```

## 生产检查清单

部署到生产环境前，请确认：

- [ ] 已部署至少 3 个管理节点（奇数）
- [ ] 已部署至少 3 个工作节点
- [ ] 已配置服务副本数 >= 3
- [ ] 已配置滚动更新策略
- [ ] 已配置资源限制（CPU/Memory）
- [ ] 已配置健康检查
- [ ] 已配置重启策略
- [ ] 已使用 Secrets 管理敏感信息
- [ ] 已配置持久化存储
- [ ] 已配置监控和日志收集
- [ ] 已配置备份策略
- [ ] 已进行故障演练
- [ ] 已准备回滚方案
- [ ] 已配置防火墙规则
- [ ] 已配置 HTTPS/TLS

## 参考资源

- [Docker Swarm 官方文档](https://docs.docker.com/engine/swarm/)
- [Docker Stack 部署](https://docs.docker.com/engine/reference/commandline/stack_deploy/)
- [Docker Secrets 管理](https://docs.docker.com/engine/swarm/secrets/)
- [Docker 最佳实践](https://docs.docker.com/develop/dev-best-practices/)
- [Swarm Mode 教程](https://docs.docker.com/engine/swarm/swarm-tutorial/)
