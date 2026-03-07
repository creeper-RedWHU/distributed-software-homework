# 🚀 Docker 一键部署指南

本文档提供秒杀系统的 Docker 快速部署方案。

## 📦 一键启动（推荐）

```bash
# 方式1：使用启动脚本（推荐）
./docker-start.sh

# 方式2：使用 docker-compose
docker-compose up -d
```

**5 分钟内完成部署！** 脚本会自动检测环境、启动所有服务并等待就绪。

## 🎯 快速访问

启动成功后，访问以下地址：

| 服务 | 地址 | 用户名/密码 |
|------|------|------------|
| **应用** | http://localhost:8080 | - |
| **Nginx** | http://localhost | - |
| **健康检查** | http://localhost:8080/actuator/health | - |
| **MySQL** | localhost:3306 | root/root |
| **Redis** | localhost:6379 | 无密码 |
| **Kafka** | localhost:9092 | - |

## 📋 前置要求

- **Docker** 20.10+
- **Docker Compose** 2.0+
- **内存**: 至少 4GB 可用
- **磁盘**: 至少 10GB 可用空间

### 安装 Docker

```bash
# macOS
brew install docker docker-compose

# Ubuntu/Debian
curl -fsSL https://get.docker.com | sh

# 验证安装
docker --version
docker-compose --version
```

## 🛠️ 部署步骤

### 开发环境（单机版）

```bash
# 1. 进入项目目录
cd demo

# 2. 一键启动所有服务
./docker-start.sh

# 3. 查看服务状态
docker-compose ps

# 4. 查看应用日志
docker-compose logs -f app
```

### 生产环境（集群版）

```bash
# 启动集群版（包含主从复制、多实例）
docker-compose -f docker-compose.yml -f docker-compose.prod.yml up -d

# 查看集群状态
docker-compose -f docker-compose.yml -f docker-compose.prod.yml ps
```

## 📊 服务架构

```
┌─────────────────────────────────────────────┐
│              Nginx (负载均衡)                 │
│            localhost:80                      │
└──────────┬──────────────────────────────────┘
           │
           ▼
┌──────────────────────────────────────────────┐
│         Spring Boot 应用集群                  │
│   app-1:8080  app-2:8080  app-3:8080        │
└──┬────────┬────────────┬────────────────────┘
   │        │            │
   ▼        ▼            ▼
┌────────┐ ┌──────────┐ ┌─────────────────┐
│ MySQL  │ │  Redis   │ │  Kafka Cluster  │
│ Master │ │  Cluster │ │  3 Brokers      │
│ Slave  │ │          │ │  + Zookeeper    │
└────────┘ └──────────┘ └─────────────────┘
```

## 🔧 常用命令

### 启动脚本命令

```bash
./docker-start.sh start      # 启动所有服务（默认）
./docker-start.sh stop       # 停止所有服务
./docker-start.sh restart    # 重启所有服务
./docker-start.sh rebuild    # 重新构建并启动
./docker-start.sh clean      # 清理所有容器和数据
./docker-start.sh logs       # 查看应用日志
./docker-start.sh status     # 查看服务状态
./docker-start.sh help       # 显示帮助信息
```

### Docker Compose 命令

```bash
# 启动服务
docker-compose up -d

# 停止服务
docker-compose down

# 重启服务
docker-compose restart

# 查看日志
docker-compose logs -f app

# 查看状态
docker-compose ps

# 进入容器
docker-compose exec app sh
docker-compose exec mysql mysql -uroot -proot seckill_db
docker-compose exec redis redis-cli

# 重新构建
docker-compose build --no-cache

# 扩展实例
docker-compose up -d --scale app=3
```

## 🧪 测试验证

### 1. 健康检查

```bash
# 检查应用健康状态
curl http://localhost:8080/actuator/health

# 预期输出：{"status":"UP"}
```

### 2. 数据库连接测试

```bash
# 进入 MySQL 容器
docker-compose exec mysql mysql -uroot -proot seckill_db

# 查看表
SHOW TABLES;
SELECT * FROM t_seckill_activity;
```

### 3. Redis 连接测试

```bash
# 进入 Redis 容器
docker-compose exec redis redis-cli

# 测试命令
PING
SET test:key "Hello Docker"
GET test:key
```

### 4. Kafka 测试

```bash
# 查看 Kafka 主题列表
docker-compose exec kafka kafka-topics --list --bootstrap-server localhost:9092

# 创建测试主题
docker-compose exec kafka kafka-topics --create --topic test --bootstrap-server localhost:9092

# 发送测试消息
docker-compose exec kafka kafka-console-producer --topic test --bootstrap-server localhost:9092
# 输入消息后按 Ctrl+C 退出

# 消费消息
docker-compose exec kafka kafka-console-consumer --topic test --from-beginning --bootstrap-server localhost:9092
```

## 🐛 故障排查

### 问题1：端口已被占用

```bash
# 查看端口占用（macOS/Linux）
lsof -i :8080
lsof -i :3306

# Windows
netstat -ano | findstr :8080

# 解决方案：修改 docker-compose.yml 中的端口映射
ports:
  - "8081:8080"  # 改为其他端口
```

### 问题2：MySQL 初始化失败

```bash
# 删除数据卷并重新初始化
docker-compose down -v
docker-compose up -d

# 查看 MySQL 日志
docker-compose logs mysql
```

### 问题3：应用启动失败

```bash
# 查看应用日志
docker-compose logs -f app

# 检查依赖服务状态
docker-compose ps

# 进入容器调试
docker-compose exec app sh
env | grep SPRING
```

### 问题4：内存不足

```bash
# 查看容器资源使用
docker stats

# 增加 Docker 内存限制（Docker Desktop）
# Settings -> Resources -> Memory -> 调整为 6GB+
```

## 📈 性能优化

### JVM 优化

编辑 `docker-compose.yml`:

```yaml
environment:
  JAVA_OPTS: >
    -Xms2g -Xmx4g
    -XX:+UseG1GC
    -XX:MaxGCPauseMillis=200
    -XX:+HeapDumpOnOutOfMemoryError
```

### MySQL 优化

```yaml
command:
  - --max_connections=1000
  - --innodb_buffer_pool_size=2G
  - --innodb_log_file_size=512M
```

### Redis 优化

```yaml
command: redis-server --maxmemory 4gb --maxmemory-policy allkeys-lru
```

## 🔒 安全建议

1. **修改默认密码**（生产环境必须）

创建 `.env` 文件：

```bash
MYSQL_ROOT_PASSWORD=your_strong_password_here
REDIS_PASSWORD=your_redis_password_here
```

2. **限制容器资源**

```yaml
deploy:
  resources:
    limits:
      cpus: '2'
      memory: 2G
```

3. **使用 HTTPS**

配置 Nginx SSL 证书（见 DEPLOY.md）

## 📦 数据备份

```bash
# 备份 MySQL 数据
docker-compose exec mysql mysqldump -uroot -proot seckill_db > backup_$(date +%Y%m%d).sql

# 备份 Redis 数据
docker-compose exec redis redis-cli SAVE
docker cp seckill-redis:/data/dump.rdb ./backup/redis-$(date +%Y%m%d).rdb

# 恢复 MySQL 数据
docker-compose exec -T mysql mysql -uroot -proot seckill_db < backup_20260307.sql
```

## 🚀 CI/CD 集成

### GitHub Actions 示例

创建 `.github/workflows/docker-deploy.yml`:

```yaml
name: Docker Deploy

on:
  push:
    branches: [main]

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: Build and Start
        run: |
          docker-compose build
          docker-compose up -d

      - name: Health Check
        run: |
          sleep 60
          curl -f http://localhost:8080/actuator/health
```

## 📚 更多资源

- **详细部署文档**: [DEPLOY.md](DEPLOY.md)
- **API 接口文档**: [doc/06-API接口设计.md](doc/06-API接口设计.md)
- **系统架构设计**: [doc/01-系统概述与架构设计.md](doc/01-系统概述与架构设计.md)
- **Docker 官方文档**: https://docs.docker.com/
- **Spring Boot Docker 指南**: https://spring.io/guides/topicals/spring-boot-docker/

## 🎉 快速开始示例

```bash
# 完整流程演示
cd demo
./docker-start.sh                  # 启动所有服务
docker-compose logs -f app         # 查看日志
curl http://localhost:8080/actuator/health  # 健康检查

# 测试秒杀接口（需要先创建测试数据）
curl -X POST http://localhost:8080/api/seckill/1/participate \
  -H "Content-Type: application/json" \
  -d '{"userId": 1}'

# 停止服务
docker-compose down
```

## 💡 提示

- 首次启动需要下载镜像，约需 5-10 分钟
- 确保 Docker Desktop 运行中
- 建议分配至少 4GB 内存给 Docker
- 开发环境使用 `docker-compose.yml`
- 生产环境使用 `docker-compose.prod.yml`
- 完整文档请查看 `DEPLOY.md`

---

**如有问题，请查看详细文档 [DEPLOY.md](DEPLOY.md) 或提交 Issue**
