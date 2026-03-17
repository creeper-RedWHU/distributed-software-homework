# 秒杀系统 - 容器化部署与负载均衡 完整指南

## 项目概述

本项目实现了一个基于 Spring Boot 的商品秒杀系统，包含以下核心功能：

1. **容器化部署** - 使用 Docker 和 docker-compose 编排 MySQL、Redis、Spring Boot、Nginx
2. **负载均衡** - 2个后端实例(8081/8082) 通过 Nginx(80) 代理，支持4种负载均衡算法
3. **动静分离** - Nginx 直接提供静态资源(HTML/CSS/JS)，动态API请求代理到后端
4. **分布式缓存** - Redis 缓存商品详情，处理缓存穿透、击穿、雪崩问题
5. **压力测试** - JMeter 测试计划验证负载均衡和缓存效果

## 目录结构

```
demo/
├── Dockerfile                          # 多阶段构建镜像
├── docker-compose.lb.yml              # 负载均衡部署编排文件
├── docker/
│   ├── nginx/
│   │   ├── nginx-lb.conf             # Nginx主配置(含4种LB算法)
│   │   └── conf.d/
│   │       └── lb.conf               # 动静分离配置
│   └── static/                        # 前端静态资源
│       ├── index.html                 # 秒杀系统前端页面
│       ├── css/style.css              # 样式
│       └── js/app.js                  # 交互逻辑
├── jmeter/
│   └── seckill-test.jmx              # JMeter测试计划
├── src/main/java/com/example/demo/
│   ├── config/
│   │   └── RedisConfig.java          # Redis序列化配置
│   ├── controller/
│   │   ├── ProductController.java     # 商品API(带缓存)
│   │   └── SeckillController.java     # 秒杀API
│   ├── filter/
│   │   └── ServerInfoFilter.java      # 响应头添加端口标识
│   ├── mapper/
│   │   ├── ProductMapper.java         # 商品数据访问
│   │   ├── SeckillProductMapper.java  # 秒杀商品数据访问
│   │   └── SeckillOrderMapper.java    # 秒杀订单数据访问
│   ├── model/entity/
│   │   ├── Product.java               # 商品实体
│   │   ├── SeckillProduct.java        # 秒杀商品实体
│   │   └── SeckillOrder.java          # 秒杀订单实体
│   └── service/
│       ├── ProductService.java        # 商品服务(Redis缓存)
│       └── SeckillService.java        # 秒杀服务
└── doc/容器与负载均衡部署/
    ├── README.md                      # 本文档
    ├── 01-容器环境部署.md
    ├── 02-负载均衡配置.md
    ├── 03-动静分离配置.md
    ├── 04-分布式缓存.md
    └── 05-JMeter压力测试.md
```

## 快速开始

### 前置条件

- Docker 20.10+ 和 Docker Compose 2.0+
- JMeter 5.5+（压力测试用）
- 确保 80、3306、6379、8081、8082 端口未被占用

### 第一步：启动全部容器

```bash
cd demo

# 构建并启动（首次需要构建镜像，耗时较长）
docker-compose -f docker-compose.lb.yml up -d --build

# 查看容器状态（等待所有服务变为 healthy）
docker-compose -f docker-compose.lb.yml ps
```

等待约60-90秒，所有服务就绪后：

| 服务 | 端口 | 状态 |
|------|------|------|
| seckill-mysql | 3306 | healthy |
| seckill-redis | 6379 | healthy |
| seckill-app1 | 8081 | healthy |
| seckill-app2 | 8082 | healthy |
| seckill-nginx | 80 | running |

### 第二步：验证部署

```bash
# 访问前端页面
open http://localhost

# 通过Nginx访问API
curl http://localhost/api/product/list

# 直接访问各后端实例
curl http://localhost:8081/api/product/list
curl http://localhost:8082/api/product/list

# 查看负载均衡分发（观察X-Server-Port）
for i in $(seq 1 6); do
  echo -n "请求$i: "
  curl -s http://localhost/api/product/server-info | python3 -c "import sys,json; print(json.load(sys.stdin)['data'])"
done
```

### 第三步：测试负载均衡

```bash
# 清空后端日志
docker logs --since 0s seckill-app1 > /dev/null 2>&1
docker logs --since 0s seckill-app2 > /dev/null 2>&1

# 发送100个请求
for i in $(seq 1 100); do
  curl -s http://localhost/api/product/server-info > /dev/null
done

# 统计各后端处理的请求数
echo "App1:" && docker logs seckill-app1 2>&1 | grep "server-info" | wc -l
echo "App2:" && docker logs seckill-app2 2>&1 | grep "server-info" | wc -l
```

### 第四步：切换负载均衡算法

编辑 `docker/nginx/nginx-lb.conf`，注释当前 upstream 块，取消注释另一个算法：

```bash
# 修改配置后重新加载
docker exec seckill-nginx nginx -s reload
```

### 第五步：测试动静分离

```bash
# 静态资源（Nginx直接返回）
curl -I http://localhost/css/style.css
# 注意: 有 X-Static-Cache: HIT，无 X-Server-Port

# 动态API（Nginx代理到后端）
curl -I http://localhost/api/product/list
# 注意: 有 X-Server-Port 和 X-Upstream-Addr
```

### 第六步：测试Redis缓存

```bash
# 查询商品（第一次走DB）
curl http://localhost/api/product/1
# 查看后端日志，有DB查询记录

# 再次查询（走Redis缓存）
curl http://localhost/api/product/1
# 后端日志显示"缓存命中"

# 查询不存在的商品（穿透防护）
curl http://localhost/api/product/99999
# 返回"商品不存在"，空值被缓存

# 查看Redis缓存
docker exec seckill-redis redis-cli keys "product:*"
```

### 第七步：JMeter 压力测试

```bash
# GUI模式
jmeter -t jmeter/seckill-test.jmx

# 命令行模式（生成HTML报告）
jmeter -n -t jmeter/seckill-test.jmx -l result.jtl -e -o report/
open report/index.html
```

## API 接口列表

### 商品接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/product/list` | GET | 商品列表 |
| `/api/product/{id}` | GET | 商品详情（Redis缓存） |
| `/api/product/add` | POST | 新增商品 |
| `/api/product/{id}/evict-cache` | POST | 清除缓存 |
| `/api/product/{id}/warm-cache` | POST | 预热缓存 |
| `/api/product/server-info` | GET | 服务器端口信息 |

### 秒杀接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/seckill/list` | GET | 秒杀活动列表 |
| `/api/seckill/{id}` | GET | 秒杀详情 |
| `/api/seckill/do?userId=&seckillId=` | POST | 执行秒杀 |
| `/api/seckill/{id}/warm-stock` | POST | 预热秒杀库存 |

### 用户认证接口（已有）

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/auth/login` | POST | 登录 |
| `/api/auth/register/buyer` | POST | 注册买家 |
| `/api/auth/register/merchant` | POST | 注册商家 |
| `/api/auth/logout` | POST | 登出 |
| `/ping/` | GET | 健康检查 |

## 常用运维命令

```bash
# 查看所有容器状态
docker-compose -f docker-compose.lb.yml ps

# 查看某服务日志
docker-compose -f docker-compose.lb.yml logs -f app1

# 重启某个服务
docker-compose -f docker-compose.lb.yml restart app1

# 重新加载Nginx配置（不停机）
docker exec seckill-nginx nginx -s reload

# 进入Redis CLI
docker exec -it seckill-redis redis-cli

# 进入MySQL CLI
docker exec -it seckill-mysql mysql -uroot -proot seckill_db

# 清除所有Redis缓存
docker exec seckill-redis redis-cli flushdb

# 停止并清理所有容器和数据
docker-compose -f docker-compose.lb.yml down -v
```

## 详细文档

- [01-容器环境部署](01-容器环境部署.md) - Dockerfile、docker-compose 详解
- [02-负载均衡配置](02-负载均衡配置.md) - 四种算法配置与验证
- [03-动静分离配置](03-动静分离配置.md) - Nginx动静分离与前端页面
- [04-分布式缓存](04-分布式缓存.md) - Redis缓存与三大问题处理
- [05-JMeter压力测试](05-JMeter压力测试.md) - 测试计划与结果分析
