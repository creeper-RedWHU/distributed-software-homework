# Docker 部署指南

## 快速开始（一键部署）

### 前置要求

- Docker 20.10+
- Docker Compose 2.0+
- 至少 4GB 可用内存
- 至少 10GB 可用磁盘空间

### 一键启动

```bash
# 1. 克隆项目（如果还未克隆）
git clone <repository-url>
cd demo

# 2. 启动所有服务（开发环境）
docker-compose up -d

# 3. 查看服务状态
docker-compose ps

# 4. 查看日志
docker-compose logs -f app
```

### 服务访问地址

| 服务 | 地址 | 说明 |
|------|------|------|
| 应用主页 | http://localhost:8080 | Spring Boot 应用 |
| Nginx 代理 | http://localhost | Nginx 反向代理 |
| MySQL | localhost:3306 | 数据库（root/root） |
| Redis | localhost:6379 | 缓存服务 |
| Kafka | localhost:9092 | 消息队列 |
| 健康检查 | http://localhost:8080/actuator/health | 应用健康状态 |

## 详细部署步骤

### 开发环境部署

```bash
# 启动所有服务（后台运行）
docker-compose up -d

# 仅启动中间件（不启动应用）
docker-compose up -d mysql redis kafka

# 本地运行应用（连接 Docker 中的中间件）
./mvnw spring-boot:run -Dspring-boot.run.profiles=docker

# 停止所有服务
docker-compose down

# 停止并删除所有数据
docker-compose down -v
```

### 生产环境部署（集群版本）

```bash
# 使用生产配置启动
docker-compose -f docker-compose.yml -f docker-compose.prod.yml up -d

# 查看集群状态
docker-compose -f docker-compose.yml -f docker-compose.prod.yml ps

# 扩展应用实例（手动扩展）
docker-compose up -d --scale app=5
```

## 常用命令

### 查看服务

```bash
# 查看运行中的容器
docker-compose ps

# 查看所有容器（包括停止的）
docker-compose ps -a

# 查看服务日志
docker-compose logs -f [service-name]

# 查看应用日志
docker-compose logs -f app

# 查看最近 100 行日志
docker-compose logs --tail=100 app
```

### 进入容器

```bash
# 进入应用容器
docker-compose exec app sh

# 进入 MySQL 容器
docker-compose exec mysql mysql -uroot -proot seckill_db

# 进入 Redis 容器
docker-compose exec redis redis-cli

# 进入 Kafka 容器
docker-compose exec kafka bash
```

### 重启服务

```bash
# 重启所有服务
docker-compose restart

# 重启指定服务
docker-compose restart app

# 重新构建并启动应用
docker-compose up -d --build app
```

### 清理资源

```bash
# 停止所有服务
docker-compose down

# 停止并删除数据卷
docker-compose down -v

# 清理未使用的镜像
docker image prune -a

# 清理所有未使用的资源
docker system prune -a --volumes
```

## 配置说明

### 环境变量配置

创建 `.env` 文件覆盖默认配置：

```bash
# MySQL
MYSQL_ROOT_PASSWORD=your_strong_password
MYSQL_DATABASE=seckill_db

# Redis
REDIS_PASSWORD=your_redis_password

# 应用
SPRING_PROFILES_ACTIVE=docker
SERVER_PORT=8080

# JVM 参数
JAVA_OPTS=-Xms1g -Xmx2g
```

### 自定义配置

修改 `src/main/resources/application-docker.yml` 调整 Docker 环境配置。

### 资源限制

在 `docker-compose.yml` 中为服务添加资源限制：

```yaml
services:
  app:
    deploy:
      resources:
        limits:
          cpus: '2'
          memory: 2G
        reservations:
          cpus: '1'
          memory: 1G
```

## 监控与健康检查

### 健康检查端点

```bash
# 应用健康状态
curl http://localhost:8080/actuator/health

# MySQL 连接测试
docker-compose exec mysql mysqladmin ping -h localhost -uroot -proot

# Redis 连接测试
docker-compose exec redis redis-cli ping

# Kafka 主题列表
docker-compose exec kafka kafka-topics --list --bootstrap-server localhost:9092
```

### 查看资源使用

```bash
# 查看容器资源使用情况
docker stats

# 查看指定容器资源使用
docker stats seckill-app seckill-mysql seckill-redis
```

## 故障排查

### 常见问题

**1. 端口已被占用**

```bash
# 查看端口占用
lsof -i :8080
netstat -ano | findstr :8080  # Windows

# 修改 docker-compose.yml 中的端口映射
ports:
  - "8081:8080"  # 改为其他端口
```

**2. MySQL 初始化失败**

```bash
# 删除数据卷重新初始化
docker-compose down -v
docker-compose up -d

# 手动执行 SQL
docker-compose exec mysql mysql -uroot -proot seckill_db < src/main/resources/sql/schema.sql
```

**3. Kafka 连接失败**

```bash
# 查看 Kafka 日志
docker-compose logs -f kafka

# 创建测试主题
docker-compose exec kafka kafka-topics --create --topic test --bootstrap-server localhost:9092

# 查看主题列表
docker-compose exec kafka kafka-topics --list --bootstrap-server localhost:9092
```

**4. 应用启动失败**

```bash
# 查看详细日志
docker-compose logs -f app

# 进入容器检查
docker-compose exec app sh
ls -la /app

# 检查依赖服务是否就绪
docker-compose ps
```

### 日志位置

```
应用日志：docker volume inspect demo_app-logs
MySQL 日志：docker-compose logs mysql
Redis 日志：docker-compose logs redis
Kafka 日志：docker-compose logs kafka
Nginx 日志：docker volume inspect demo_nginx-logs
```

## 备份与恢复

### 数据备份

```bash
# 备份 MySQL 数据
docker-compose exec mysql mysqldump -uroot -proot seckill_db > backup.sql

# 备份 Redis 数据
docker-compose exec redis redis-cli SAVE
docker cp seckill-redis:/data/dump.rdb ./backup/redis-dump.rdb

# 备份所有数据卷
docker run --rm -v demo_mysql-data:/data -v $(pwd)/backup:/backup alpine tar czf /backup/mysql-backup.tar.gz /data
```

### 数据恢复

```bash
# 恢复 MySQL 数据
docker-compose exec -T mysql mysql -uroot -proot seckill_db < backup.sql

# 恢复 Redis 数据
docker cp ./backup/redis-dump.rdb seckill-redis:/data/dump.rdb
docker-compose restart redis
```

## 性能优化

### 1. JVM 调优

```bash
# 修改 docker-compose.yml 中的 JAVA_OPTS
environment:
  JAVA_OPTS: >
    -Xms2g -Xmx4g
    -XX:+UseG1GC
    -XX:MaxGCPauseMillis=200
    -XX:+HeapDumpOnOutOfMemoryError
    -XX:HeapDumpPath=/app/logs/
```

### 2. MySQL 优化

```bash
# 修改 docker-compose.yml 中的 MySQL 参数
command:
  - --max_connections=1000
  - --innodb_buffer_pool_size=1G
  - --innodb_log_file_size=256M
```

### 3. Redis 优化

```bash
# 修改 Redis 配置
command: redis-server --maxmemory 2gb --maxmemory-policy allkeys-lru
```

## CI/CD 集成

### GitHub Actions 示例

```yaml
name: Docker Build and Deploy

on:
  push:
    branches: [main]

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Build and Deploy
        run: |
          docker-compose build
          docker-compose up -d
```

## 安全建议

1. **生产环境必须修改默认密码**
2. **使用环境变量存储敏感信息**
3. **启用 HTTPS（配置 SSL 证书）**
4. **限制容器资源使用**
5. **定期更新基础镜像**
6. **使用 Docker secrets 管理密钥**

## 更多信息

- [Docker 官方文档](https://docs.docker.com/)
- [Docker Compose 文档](https://docs.docker.com/compose/)
- [Spring Boot Docker 最佳实践](https://spring.io/guides/topicals/spring-boot-docker/)
