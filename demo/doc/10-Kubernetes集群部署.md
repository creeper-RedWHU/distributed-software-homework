# Kubernetes 集群部署指南

## 目录

- [概述](#概述)
- [架构设计](#架构设计)
- [前置要求](#前置要求)
- [快速开始](#快速开始)
- [详细部署步骤](#详细部署步骤)
- [配置说明](#配置说明)
- [运维管理](#运维管理)
- [监控与日志](#监控与日志)
- [故障排查](#故障排查)
- [最佳实践](#最佳实践)

## 概述

本文档介绍如何将秒杀系统部署到 Kubernetes 集群，实现生产级的高可用、弹性伸缩和滚动更新。

### Kubernetes 部署优势

- **自动化编排**: 自动调度容器到合适的节点
- **自愈能力**: 自动重启失败的容器，替换和重新调度
- **弹性伸缩**: 根据负载自动扩缩容（HPA）
- **服务发现**: 内置 DNS 和负载均衡
- **滚动更新**: 零停机时间的应用升级
- **配置管理**: ConfigMap 和 Secret 管理配置
- **存储编排**: 自动挂载存储系统

## 架构设计

### 集群拓扑

```
┌─────────────────────────────────────────────────────────────┐
│                    Kubernetes Cluster                        │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌──────────────────────────────────────────────────────┐   │
│  │            Ingress Controller (Nginx)                 │   │
│  │         外部访问入口 + SSL 终止 + 限流               │   │
│  └────────────────────┬─────────────────────────────────┘   │
│                       │                                       │
│  ┌────────────────────┴─────────────────────────────────┐   │
│  │               Service (ClusterIP)                     │   │
│  │              seckill-app-service                      │   │
│  └────────────────────┬─────────────────────────────────┘   │
│                       │                                       │
│  ┌────────────────────┴─────────────────────────────────┐   │
│  │        Deployment: seckill-app (3 replicas)          │   │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐           │   │
│  │  │  Pod 1   │  │  Pod 2   │  │  Pod 3   │           │   │
│  │  │App + JVM │  │App + JVM │  │App + JVM │           │   │
│  │  └──────────┘  └──────────┘  └──────────┘           │   │
│  └───────────────┬────────────────┬────────────────────┘   │
│                  │                │                          │
│  ┌───────────────┴────┬───────────┴──────┬─────────────┐   │
│  │                    │                  │             │   │
│  ▼                    ▼                  ▼             ▼   │
│ ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│ │StatefulSet   │  │StatefulSet   │  │StatefulSet   │      │
│ │MySQL Master  │  │Redis Cluster │  │Kafka Cluster │      │
│ │MySQL Slave   │  │(3 nodes)     │  │(3 brokers)   │      │
│ │PVC           │  │PVC           │  │PVC + ZK      │      │
│ └──────────────┘  └──────────────┘  └──────────────┘      │
│                                                             │
│ ┌─────────────────────────────────────────────────────┐   │
│ │     Persistent Volumes (NFS/Ceph/Cloud Storage)     │   │
│ └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### 资源规划

| 组件 | Replicas | CPU Request | CPU Limit | Memory Request | Memory Limit |
|------|----------|-------------|-----------|----------------|--------------|
| App | 3-10 | 500m | 2000m | 1Gi | 2Gi |
| MySQL | 1 Master + 1 Slave | 1000m | 2000m | 2Gi | 4Gi |
| Redis | 3 | 200m | 500m | 512Mi | 1Gi |
| Kafka | 3 | 500m | 1000m | 1Gi | 2Gi |
| Zookeeper | 3 | 200m | 500m | 512Mi | 1Gi |
| Nginx Ingress | 2 | 200m | 500m | 256Mi | 512Mi |

## 前置要求

### 1. Kubernetes 集群

- Kubernetes 1.24+
- kubectl 客户端工具
- 至少 3 个 Worker 节点（生产环境）
- 节点配置：4 Core CPU, 8GB RAM

### 2. 存储

- 支持 PersistentVolume（NFS、Ceph、云存储等）
- StorageClass 配置
- 至少 100GB 可用存储空间

### 3. 网络

- Ingress Controller（推荐 Nginx Ingress）
- 外部负载均衡器（云环境）或 NodePort
- DNS 解析配置

### 4. 工具

```bash
# 安装 kubectl
curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
chmod +x kubectl
sudo mv kubectl /usr/local/bin/

# 安装 Helm（可选）
curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash

# 验证安装
kubectl version --client
helm version
```

## 快速开始

### 一键部署

```bash
# 1. 进入 K8s 配置目录
cd /Users/mac/Desktop/project/distributed-software-homework/demo/k8s

# 2. 创建命名空间
kubectl apply -f namespace.yaml

# 3. 部署所有资源
kubectl apply -f .

# 4. 等待所有 Pod 就绪
kubectl wait --for=condition=ready pod -l app=seckill-app -n seckill --timeout=300s

# 5. 查看服务状态
kubectl get all -n seckill

# 6. 获取访问地址
kubectl get ingress -n seckill
```

### 使用部署脚本

```bash
# 使用自动化脚本
./k8s/deploy.sh start

# 查看帮助
./k8s/deploy.sh help
```

## 详细部署步骤

### 步骤 1: 创建命名空间和配置

```bash
# 创建命名空间
kubectl create namespace seckill

# 创建 ConfigMap（应用配置）
kubectl apply -f k8s/configmap.yaml

# 创建 Secret（敏感信息）
kubectl create secret generic mysql-secret \
  --from-literal=root-password='your_root_password' \
  --from-literal=user-password='your_user_password' \
  -n seckill

kubectl create secret generic redis-secret \
  --from-literal=password='your_redis_password' \
  -n seckill
```

### 步骤 2: 部署中间件

#### 2.1 部署 MySQL

```bash
# 创建 PersistentVolumeClaim
kubectl apply -f k8s/mysql-pvc.yaml

# 部署 MySQL StatefulSet
kubectl apply -f k8s/mysql-statefulset.yaml

# 创建 MySQL Service
kubectl apply -f k8s/mysql-service.yaml

# 验证 MySQL
kubectl exec -it mysql-0 -n seckill -- mysql -uroot -p
```

#### 2.2 部署 Redis

```bash
# 部署 Redis Cluster
kubectl apply -f k8s/redis-statefulset.yaml
kubectl apply -f k8s/redis-service.yaml

# 验证 Redis
kubectl exec -it redis-0 -n seckill -- redis-cli ping
```

#### 2.3 部署 Kafka

```bash
# 部署 Zookeeper
kubectl apply -f k8s/zookeeper-statefulset.yaml
kubectl apply -f k8s/zookeeper-service.yaml

# 部署 Kafka
kubectl apply -f k8s/kafka-statefulset.yaml
kubectl apply -f k8s/kafka-service.yaml

# 验证 Kafka
kubectl exec -it kafka-0 -n seckill -- kafka-topics.sh --list --bootstrap-server localhost:9092
```

### 步骤 3: 构建并推送应用镜像

```bash
# 构建镜像
docker build -t your-registry.com/seckill-app:v1.0.0 .

# 推送到镜像仓库
docker push your-registry.com/seckill-app:v1.0.0

# 如果使用私有仓库，创建 imagePullSecret
kubectl create secret docker-registry regcred \
  --docker-server=your-registry.com \
  --docker-username=your-username \
  --docker-password=your-password \
  --docker-email=your-email \
  -n seckill
```

### 步骤 4: 部署应用

```bash
# 部署应用 Deployment
kubectl apply -f k8s/app-deployment.yaml

# 创建应用 Service
kubectl apply -f k8s/app-service.yaml

# 配置 Ingress
kubectl apply -f k8s/ingress.yaml

# 检查部署状态
kubectl rollout status deployment/seckill-app -n seckill
```

### 步骤 5: 配置自动伸缩

```bash
# 部署 Horizontal Pod Autoscaler
kubectl apply -f k8s/hpa.yaml

# 查看 HPA 状态
kubectl get hpa -n seckill
```

### 步骤 6: 验证部署

```bash
# 检查所有资源
kubectl get all -n seckill

# 检查 Pod 日志
kubectl logs -f deployment/seckill-app -n seckill

# 测试应用健康检查
kubectl exec -it deployment/seckill-app -n seckill -- wget -qO- http://localhost:8080/actuator/health

# 测试外部访问
curl http://your-domain.com/api/seckill/activities
```

## 配置说明

### ConfigMap 配置

管理应用的非敏感配置，如数据库连接、Redis 地址等。

```yaml
# 查看 ConfigMap
kubectl get configmap seckill-config -n seckill -o yaml

# 更新 ConfigMap
kubectl edit configmap seckill-config -n seckill

# 重启应用使配置生效
kubectl rollout restart deployment/seckill-app -n seckill
```

### Secret 配置

管理敏感信息，如密码、密钥等。

```bash
# 查看 Secret（Base64 编码）
kubectl get secret mysql-secret -n seckill -o yaml

# 解码查看
kubectl get secret mysql-secret -n seckill -o jsonpath='{.data.root-password}' | base64 -d

# 更新 Secret
kubectl create secret generic mysql-secret \
  --from-literal=root-password='new_password' \
  --dry-run=client -o yaml | kubectl apply -f -
```

### 资源配额

```yaml
# 设置命名空间资源配额
kubectl apply -f k8s/resource-quota.yaml

# 查看配额使用情况
kubectl describe resourcequota -n seckill
```

## 运维管理

### 应用更新

#### 滚动更新

```bash
# 更新镜像版本
kubectl set image deployment/seckill-app \
  seckill-app=your-registry.com/seckill-app:v1.1.0 \
  -n seckill

# 查看更新状态
kubectl rollout status deployment/seckill-app -n seckill

# 查看更新历史
kubectl rollout history deployment/seckill-app -n seckill
```

#### 回滚

```bash
# 回滚到上一个版本
kubectl rollout undo deployment/seckill-app -n seckill

# 回滚到指定版本
kubectl rollout undo deployment/seckill-app --to-revision=2 -n seckill
```

### 扩缩容

#### 手动扩缩容

```bash
# 扩容到 5 个副本
kubectl scale deployment/seckill-app --replicas=5 -n seckill

# 缩容到 2 个副本
kubectl scale deployment/seckill-app --replicas=2 -n seckill
```

#### 自动扩缩容

```bash
# 配置 HPA（CPU 目标 70%）
kubectl autoscale deployment seckill-app \
  --cpu-percent=70 \
  --min=3 \
  --max=10 \
  -n seckill

# 查看 HPA 状态
kubectl get hpa -n seckill -w
```

### 配置热更新

```bash
# 更新 ConfigMap
kubectl create configmap seckill-config \
  --from-file=application.yml \
  --dry-run=client -o yaml | kubectl apply -f -

# 滚动重启应用
kubectl rollout restart deployment/seckill-app -n seckill
```

### 数据备份

```bash
# 备份 MySQL 数据
kubectl exec mysql-0 -n seckill -- mysqldump -uroot -p$MYSQL_ROOT_PASSWORD seckill_db > backup.sql

# 备份到持久化存储
kubectl exec mysql-0 -n seckill -- sh -c \
  "mysqldump -uroot -p$MYSQL_ROOT_PASSWORD seckill_db | gzip > /var/lib/mysql/backup/backup-$(date +%Y%m%d).sql.gz"

# 备份 Redis 数据
kubectl exec redis-0 -n seckill -- redis-cli SAVE
kubectl cp redis-0:/data/dump.rdb ./redis-backup.rdb -n seckill
```

## 监控与日志

### Prometheus + Grafana 监控

```bash
# 安装 Prometheus Operator
kubectl apply -f https://raw.githubusercontent.com/prometheus-operator/prometheus-operator/main/bundle.yaml

# 部署 ServiceMonitor
kubectl apply -f k8s/monitoring/service-monitor.yaml

# 访问 Grafana
kubectl port-forward svc/grafana 3000:80 -n monitoring
# 访问 http://localhost:3000
```

### 日志收集

#### 使用 EFK Stack

```bash
# 部署 Elasticsearch
kubectl apply -f k8s/logging/elasticsearch.yaml

# 部署 Fluentd
kubectl apply -f k8s/logging/fluentd-daemonset.yaml

# 部署 Kibana
kubectl apply -f k8s/logging/kibana.yaml

# 访问 Kibana
kubectl port-forward svc/kibana 5601:5601 -n logging
```

#### 查看实时日志

```bash
# 查看应用日志
kubectl logs -f deployment/seckill-app -n seckill

# 查看多个 Pod 日志
kubectl logs -f -l app=seckill-app -n seckill --max-log-requests=10

# 查看前 100 行日志
kubectl logs --tail=100 deployment/seckill-app -n seckill

# 查看最近 1 小时的日志
kubectl logs --since=1h deployment/seckill-app -n seckill
```

### 性能指标

```bash
# 查看 Pod 资源使用
kubectl top pod -n seckill

# 查看 Node 资源使用
kubectl top node

# 查看 HPA 指标
kubectl get hpa -n seckill -w
```

## 故障排查

### 常见问题

#### 1. Pod 一直处于 Pending 状态

```bash
# 查看 Pod 详情
kubectl describe pod <pod-name> -n seckill

# 常见原因：
# - 资源不足（CPU/Memory）
# - PVC 未绑定
# - 镜像拉取失败
# - 节点亲和性不满足

# 解决方案：
kubectl get pv,pvc -n seckill
kubectl get nodes
kubectl describe node <node-name>
```

#### 2. Pod 频繁重启

```bash
# 查看 Pod 事件
kubectl get events -n seckill --sort-by='.lastTimestamp'

# 查看容器日志
kubectl logs <pod-name> -n seckill --previous

# 常见原因：
# - 应用启动失败
# - 健康检查失败
# - OOMKilled（内存不足）
# - CrashLoopBackOff

# 解决方案：
kubectl describe pod <pod-name> -n seckill
kubectl logs <pod-name> -n seckill --previous
```

#### 3. 服务无法访问

```bash
# 检查 Service
kubectl get svc -n seckill
kubectl describe svc seckill-app-service -n seckill

# 检查 Endpoints
kubectl get endpoints seckill-app-service -n seckill

# 检查 Ingress
kubectl describe ingress seckill-ingress -n seckill

# 测试 Service 连通性
kubectl run test-pod --image=busybox -it --rm -n seckill -- wget -qO- http://seckill-app-service:8080/actuator/health
```

#### 4. 数据库连接失败

```bash
# 检查 MySQL Pod
kubectl get pod -l app=mysql -n seckill

# 测试数据库连接
kubectl exec -it mysql-0 -n seckill -- mysql -uroot -p -e "SHOW DATABASES;"

# 检查网络策略
kubectl get networkpolicy -n seckill

# 从应用 Pod 测试连接
kubectl exec -it deployment/seckill-app -n seckill -- sh
nc -zv mysql-service 3306
```

### 调试工具

```bash
# 进入 Pod 调试
kubectl exec -it <pod-name> -n seckill -- sh

# 启动临时调试容器
kubectl debug <pod-name> -n seckill --image=busybox

# 拷贝文件到本地
kubectl cp seckill/<pod-name>:/app/logs/app.log ./app.log

# 端口转发到本地
kubectl port-forward svc/seckill-app-service 8080:8080 -n seckill
```

## 最佳实践

### 1. 资源管理

- 始终设置 resources requests 和 limits
- 使用 LimitRange 设置默认值
- 使用 ResourceQuota 限制命名空间资源
- 监控资源使用情况，及时调整

### 2. 健康检查

- 配置 livenessProbe 和 readinessProbe
- 合理设置检查间隔和超时时间
- 使用专门的健康检查端点
- startupProbe 用于慢启动应用

### 3. 安全性

- 使用 RBAC 控制权限
- 不在镜像中存储敏感信息
- 使用 Secret 管理密码和密钥
- 定期更新镜像和依赖
- 使用 NetworkPolicy 限制网络访问
- 启用 Pod Security Policy

### 4. 高可用

- 至少 3 个副本
- 使用 Pod Anti-Affinity 分散到不同节点
- 配置 PodDisruptionBudget
- 使用多可用区部署

### 5. 配置管理

- 使用 ConfigMap 管理配置
- 使用 Secret 管理敏感信息
- 环境变量注入配置
- 支持配置热更新

### 6. 日志和监控

- 集中日志收集（EFK/Loki）
- 指标监控（Prometheus）
- 分布式追踪（Jaeger）
- 告警配置（AlertManager）

### 7. CI/CD 集成

- 使用 GitOps（ArgoCD/Flux）
- 自动化测试和部署
- 金丝雀发布
- 蓝绿部署

### 8. 成本优化

- 使用 HPA 自动伸缩
- 合理设置资源配额
- 使用 Spot 实例（云环境）
- 定期清理未使用资源

## 高级配置

### 金丝雀发布

```yaml
# 使用 Flagger 实现金丝雀发布
apiVersion: flagger.app/v1beta1
kind: Canary
metadata:
  name: seckill-app
  namespace: seckill
spec:
  targetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: seckill-app
  progressDeadlineSeconds: 60
  service:
    port: 8080
  analysis:
    interval: 1m
    threshold: 5
    maxWeight: 50
    stepWeight: 10
    metrics:
    - name: request-success-rate
      thresholdRange:
        min: 99
      interval: 1m
```

### 蓝绿部署

```bash
# 创建新版本部署（绿）
kubectl apply -f k8s/app-deployment-v2.yaml

# 验证新版本
kubectl exec -it deployment/seckill-app-v2 -n seckill -- wget -qO- http://localhost:8080/actuator/health

# 切换流量到新版本
kubectl patch svc seckill-app-service -n seckill -p '{"spec":{"selector":{"version":"v2"}}}'

# 回滚（如果需要）
kubectl patch svc seckill-app-service -n seckill -p '{"spec":{"selector":{"version":"v1"}}}'
```

### 服务网格（Istio）

```bash
# 安装 Istio
istioctl install --set profile=demo

# 启用自动注入
kubectl label namespace seckill istio-injection=enabled

# 部署 VirtualService 和 DestinationRule
kubectl apply -f k8s/istio/virtual-service.yaml
kubectl apply -f k8s/istio/destination-rule.yaml
```

## 生产检查清单

部署到生产环境前，请确认以下事项：

- [ ] 已配置资源 requests 和 limits
- [ ] 已配置健康检查（liveness/readiness probe）
- [ ] 已设置副本数 >= 3
- [ ] 已配置 HPA 自动伸缩
- [ ] 已配置 PodDisruptionBudget
- [ ] 已配置持久化存储（PVC）
- [ ] 已设置 Secret 管理敏感信息
- [ ] 已配置日志收集
- [ ] 已配置监控告警
- [ ] 已配置备份策略
- [ ] 已进行压力测试
- [ ] 已配置 Ingress 和 TLS
- [ ] 已配置资源配额（ResourceQuota）
- [ ] 已配置网络策略（NetworkPolicy）
- [ ] 已准备回滚方案
- [ ] 已准备灾难恢复计划

## 参考资源

- [Kubernetes 官方文档](https://kubernetes.io/docs/)
- [Kubernetes 最佳实践](https://kubernetes.io/docs/concepts/configuration/overview/)
- [Spring Boot on Kubernetes](https://spring.io/guides/topicals/spring-boot-kubernetes/)
- [12 Factor App](https://12factor.net/)
- [CNCF Landscape](https://landscape.cncf.io/)
