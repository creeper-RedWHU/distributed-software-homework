# 秒杀系统微服务拆分设计方案

## 目录

- [概述](#概述)
- [微服务拆分原则](#微服务拆分原则)
- [服务拆分方案](#服务拆分方案)
- [服务详细设计](#服务详细设计)
- [服务间通信](#服务间通信)
- [数据一致性方案](#数据一致性方案)
- [部署架构](#部署架构)
- [技术选型](#技术选型)
- [迁移路径](#迁移路径)

## 概述

### 当前架构问题

**单体架构缺陷**：
- 所有功能耦合在一个应用中
- 无法独立扩展高负载模块（秒杀服务）
- 代码冗余，维护困难
- 单点故障影响整个系统
- 团队协作困难

### 微服务架构优势

- **独立部署**：各服务独立开发、测试、部署
- **弹性伸缩**：按需扩展高负载服务
- **故障隔离**：单个服务故障不影响其他服务
- **技术异构**：不同服务可使用不同技术栈
- **团队自治**：每个团队负责特定服务

## 微服务拆分原则

### 1. 领域驱动设计（DDD）

按业务领域拆分，每个服务对应一个限界上下文：

```
秒杀系统领域模型
├── 用户域 (User Domain)
├── 商品域 (Product Domain)
├── 订单域 (Order Domain)
├── 库存域 (Inventory Domain)
├── 秒杀域 (Seckill Domain)
└── 支付域 (Payment Domain)
```

### 2. 单一职责原则

每个服务只负责一个核心业务功能，避免职责混乱。

### 3. 高内聚低耦合

- **高内聚**：相关功能聚合在同一服务
- **低耦合**：服务间通过标准接口通信

### 4. 数据独立性

每个服务拥有独立的数据库，避免数据耦合。

## 服务拆分方案

### 架构总览

```
┌─────────────────────────────────────────────────────────────────┐
│                          API Gateway                             │
│              (路由、认证、限流、熔断、聚合)                       │
└─────────┬───────────┬───────────┬───────────┬───────────────────┘
          │           │           │           │
┌─────────▼─────┐ ┌──▼────────┐ ┌▼──────────┐ ┌▼────────────────┐
│  用户服务      │ │ 商品服务   │ │ 秒杀服务   │ │  订单服务        │
│  User Service │ │Product Svc │ │Seckill Svc│ │ Order Service   │
│               │ │            │ │           │ │                 │
│  - 注册登录   │ │ - 商品管理 │ │ - 活动管理│ │  - 订单创建     │
│  - 用户信息   │ │ - 分类管理 │ │ - 秒杀执行│ │  - 订单查询     │
│  - 权限管理   │ │ - 商品查询 │ │ - 结果查询│ │  - 订单支付     │
└───────────────┘ └────────────┘ └───────────┘ └─────────────────┘
          │              │               │              │
┌─────────▼─────┐ ┌──────▼──────┐ ┌─────▼──────┐ ┌───▼──────────┐
│  库存服务      │ │  通知服务    │ │ 支付服务   │ │ 配置中心     │
│Inventory Svc  │ │Notify Service│ │Payment Svc │ │Config Center │
│               │ │              │ │            │ │              │
│ - 库存扣减    │ │ - WebSocket  │ │ - 支付回调 │ │ - 服务配置   │
│ - 库存回滚    │ │ - 消息推送   │ │ - 退款处理 │ │ - 配置刷新   │
│ - 库存对账    │ │ - 短信/邮件  │ │            │ │              │
└───────────────┘ └──────────────┘ └────────────┘ └──────────────┘
          │              │               │
┌─────────▼──────────────▼───────────────▼─────────────────────────┐
│                        基础设施层                                  │
│  ┌────────────┐  ┌────────────┐  ┌─────────────┐  ┌──────────┐ │
│  │   MySQL    │  │   Redis    │  │    Kafka    │  │  Nacos   │ │
│  │  (分库)    │  │  (缓存)    │  │  (消息队列) │  │(注册中心)│ │
│  └────────────┘  └────────────┘  └─────────────┘  └──────────┘ │
└───────────────────────────────────────────────────────────────────┘
```

### 服务列表

| 服务名称 | 端口 | 数据库 | 主要职责 |
|---------|------|--------|---------|
| **API Gateway** | 8080 | - | 统一网关、路由、鉴权 |
| **User Service** | 8081 | user_db | 用户管理 |
| **Product Service** | 8082 | product_db | 商品管理 |
| **Seckill Service** | 8083 | seckill_db | 秒杀核心逻辑 |
| **Order Service** | 8084 | order_db | 订单管理 |
| **Inventory Service** | 8085 | inventory_db | 库存管理 |
| **Payment Service** | 8086 | payment_db | 支付处理 |
| **Notification Service** | 8087 | - | 通知推送 |

## 服务详细设计

### 1. 用户服务 (User Service)

#### 职责边界

- **核心功能**：
  - 用户注册、登录、注销
  - 用户信息管理（增删改查）
  - JWT Token 生成和验证
  - 用户权限管理
  - 用户行为日志

#### 数据模型

```sql
-- 用户表
CREATE TABLE t_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(256) NOT NULL,
    phone VARCHAR(20),
    email VARCHAR(200),
    nickname VARCHAR(100),
    avatar_url VARCHAR(500),
    status TINYINT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_phone(phone),
    INDEX idx_email(email)
);

-- 用户角色表
CREATE TABLE t_user_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    role_code VARCHAR(50) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE INDEX uk_user_role(user_id, role_code)
);
```

#### 接口定义

```java
// REST API
GET    /api/user/{id}              // 获取用户信息
POST   /api/user/register          // 用户注册
POST   /api/user/login             // 用户登录
PUT    /api/user/{id}              // 更新用户信息
DELETE /api/user/{id}              // 删除用户

// 内部 RPC 接口（Feign）
@FeignClient("user-service")
interface UserServiceClient {
    UserDTO getUserById(Long userId);
    boolean checkUserPermission(Long userId, String permission);
    List<UserDTO> getUsersByIds(List<Long> userIds);
}
```

---

### 2. 商品服务 (Product Service)

#### 职责边界

- **核心功能**：
  - 商品CRUD（创建、查询、更新、删除）
  - 商品分类管理
  - 商品搜索（ES集成）
  - 商品详情缓存
  - 商品状态管理

#### 数据模型

```sql
-- 商品分类表
CREATE TABLE t_category (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    parent_id BIGINT DEFAULT 0,
    sort_order INT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_parent(parent_id)
);

-- 商品表
CREATE TABLE t_product (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    price DECIMAL(10,2) NOT NULL,
    image_url VARCHAR(500),
    category_id BIGINT NOT NULL,
    status TINYINT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_category(category_id),
    INDEX idx_status(status)
);
```

#### 接口定义

```java
// REST API
GET    /api/product/{id}           // 获取商品详情
GET    /api/product/list           // 商品列表（分页）
POST   /api/product                // 创建商品
PUT    /api/product/{id}           // 更新商品
DELETE /api/product/{id}           // 删除商品

// 内部 RPC 接口
@FeignClient("product-service")
interface ProductServiceClient {
    ProductDTO getProductById(Long productId);
    List<ProductDTO> getProductsByIds(List<Long> productIds);
    boolean checkProductStatus(Long productId);
}
```

---

### 3. 秒杀服务 (Seckill Service) ⭐核心

#### 职责边界

- **核心功能**：
  - 秒杀活动管理（创建、查询、取消）
  - 秒杀执行（调用库存服务扣减）
  - 限流控制（令牌桶、滑动窗口）
  - 活动预热（提前加载库存到Redis）
  - 秒杀结果查询

#### 数据模型

```sql
-- 秒杀活动表
CREATE TABLE t_seckill_activity (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(200) NOT NULL,
    product_id BIGINT NOT NULL,
    seckill_price DECIMAL(10,2) NOT NULL,
    total_stock INT NOT NULL,
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    status TINYINT DEFAULT 0,
    limit_per_user INT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_product(product_id),
    INDEX idx_status_time(status, start_time, end_time)
);

-- 秒杀记录表（用户参与记录）
CREATE TABLE t_seckill_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    activity_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    order_no VARCHAR(64),
    status TINYINT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE INDEX uk_activity_user(activity_id, user_id),
    INDEX idx_user(user_id)
);
```

#### 接口定义

```java
// REST API
GET    /api/seckill/activities         // 活动列表
GET    /api/seckill/activities/{id}    // 活动详情
POST   /api/seckill/execute            // 执行秒杀 ⭐
GET    /api/seckill/result/{userId}    // 查询结果

// 内部 RPC 接口
@FeignClient("seckill-service")
interface SeckillServiceClient {
    SeckillActivityDTO getActivity(Long activityId);
    boolean checkActivityStatus(Long activityId);
}
```

#### 秒杀核心流程

```
用户请求秒杀
    │
    ├─ 1. 限流检查（令牌桶）
    │       └─ 失败 → 返回"系统繁忙"
    │
    ├─ 2. 活动校验（时间、状态）
    │       └─ 失败 → 返回"活动未开始/已结束"
    │
    ├─ 3. 用户资格校验（是否已参与）
    │       └─ 失败 → 返回"已参与过"
    │
    ├─ 4. 调用库存服务扣减库存（RPC）
    │       └─ 失败 → 返回"库存不足"
    │
    ├─ 5. 发送 Kafka 消息（异步创建订单）
    │
    └─ 6. 返回秒杀成功
```

---

### 4. 订单服务 (Order Service)

#### 职责边界

- **核心功能**：
  - 订单创建（消费Kafka消息）
  - 订单查询（用户订单列表、详情）
  - 订单状态管理（待支付、已支付、已取消等）
  - 订单超时自动取消
  - 订单支付回调处理

#### 数据模型

```sql
-- 订单表
CREATE TABLE t_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(64) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    activity_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    total_amount DECIMAL(10,2) NOT NULL,
    status TINYINT DEFAULT 0, -- 0待支付 1已支付 2已取消 3已退款 4已超时
    pay_time DATETIME,
    expire_time DATETIME NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user(user_id),
    INDEX idx_activity(activity_id),
    INDEX idx_status(status),
    INDEX idx_expire_time(expire_time, status)
);

-- 订单详情表
CREATE TABLE t_order_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(64) NOT NULL,
    product_id BIGINT NOT NULL,
    product_name VARCHAR(200),
    quantity INT NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    INDEX idx_order(order_no)
);
```

#### 接口定义

```java
// REST API
GET    /api/order/{orderNo}        // 订单详情
GET    /api/order/user/{userId}    // 用户订单列表
POST   /api/order/{orderNo}/pay    // 支付订单
POST   /api/order/{orderNo}/cancel // 取消订单

// 内部 RPC 接口
@FeignClient("order-service")
interface OrderServiceClient {
    OrderDTO getOrderByNo(String orderNo);
    boolean updateOrderStatus(String orderNo, Integer status);
}

// Kafka 消息消费
@KafkaListener(topics = "seckill-order-topic")
void handleSeckillOrderMessage(SeckillOrderMessage msg);
```

---

### 5. 库存服务 (Inventory Service) ⭐核心

#### 职责边界

- **核心功能**：
  - 库存初始化（活动预热）
  - 库存扣减（Redis原子操作 + MySQL持久化）
  - 库存回滚（订单取消/超时）
  - 库存对账（Redis vs MySQL）
  - 库存实时查询

#### 数据模型

```sql
-- 库存表
CREATE TABLE t_inventory (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    activity_id BIGINT NOT NULL UNIQUE,
    product_id BIGINT NOT NULL,
    total_stock INT NOT NULL,
    available_stock INT NOT NULL,
    locked_stock INT DEFAULT 0,
    version INT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_product(product_id)
);

-- 库存流水表
CREATE TABLE t_inventory_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    activity_id BIGINT NOT NULL,
    order_no VARCHAR(64),
    user_id BIGINT,
    quantity INT NOT NULL,
    type TINYINT NOT NULL, -- 1扣减 2回滚 3补货
    before_stock INT,
    after_stock INT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_activity(activity_id),
    INDEX idx_order(order_no)
);
```

#### 接口定义

```java
// 内部 RPC 接口（仅内部调用）
@FeignClient("inventory-service")
interface InventoryServiceClient {
    // 扣减库存（Redis Lua原子操作）
    boolean deductStock(Long activityId, Long userId, Integer quantity);

    // 回滚库存
    boolean rollbackStock(Long activityId, String orderNo, Integer quantity);

    // 查询库存
    Integer getAvailableStock(Long activityId);

    // 预热库存（加载到Redis）
    void warmupStock(Long activityId);

    // 库存对账
    void reconcileStock(Long activityId);
}
```

#### Redis Lua 脚本（原子扣减）

```lua
-- seckill_deduct_v2.lua
local key = KEYS[1]              -- stock:activity:{activityId}
local user_key = KEYS[2]         -- stock:user:{activityId}:{userId}
local userId = ARGV[1]
local quantity = tonumber(ARGV[2])
local limit = tonumber(ARGV[3])

-- 检查用户是否已参与
if redis.call('EXISTS', user_key) == 1 then
    return -2  -- 已参与
end

-- 检查库存
local stock = tonumber(redis.call('GET', key) or '0')
if stock < quantity then
    return -1  -- 库存不足
end

-- 扣减库存
redis.call('DECRBY', key, quantity)
redis.call('SET', user_key, 1)
redis.call('EXPIRE', user_key, 86400)

return 1  -- 成功
```

---

### 6. 支付服务 (Payment Service)

#### 职责边界

- **核心功能**：
  - 对接第三方支付（微信、支付宝）
  - 支付回调处理
  - 退款处理
  - 支付记录查询
  - 账务对账

#### 数据模型

```sql
-- 支付记录表
CREATE TABLE t_payment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    payment_no VARCHAR(64) NOT NULL UNIQUE,
    order_no VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    payment_type TINYINT NOT NULL, -- 1微信 2支付宝 3余额
    status TINYINT DEFAULT 0, -- 0待支付 1已支付 2已退款
    transaction_id VARCHAR(200), -- 第三方交易号
    pay_time DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_order(order_no),
    INDEX idx_user(user_id)
);
```

---

### 7. 通知服务 (Notification Service)

#### 职责边界

- **核心功能**：
  - WebSocket 实时推送
  - 短信通知（阿里云SMS）
  - 邮件通知
  - 站内信
  - 消息模板管理

#### 接口定义

```java
// WebSocket 推送
@MessageMapping("/seckill/result")
void pushSeckillResult(Long userId, SeckillResultVO result);

// 内部 RPC 接口
@FeignClient("notification-service")
interface NotificationServiceClient {
    void sendSMS(String phone, String template, Map<String, Object> params);
    void sendEmail(String email, String subject, String content);
    void pushWebSocket(Long userId, String message);
}
```

---

### 8. API Gateway (Spring Cloud Gateway)

#### 职责边界

- **核心功能**：
  - 统一入口路由
  - 认证鉴权（JWT验证）
  - 限流熔断（Sentinel）
  - 请求聚合（GraphQL可选）
  - 日志记录
  - 监控统计

#### 路由配置

```yaml
spring:
  cloud:
    gateway:
      routes:
        # 用户服务
        - id: user-service
          uri: lb://user-service
          predicates:
            - Path=/api/user/**
          filters:
            - StripPrefix=1
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 10
                redis-rate-limiter.burstCapacity: 20

        # 秒杀服务（高优先级限流）
        - id: seckill-service
          uri: lb://seckill-service
          predicates:
            - Path=/api/seckill/**
          filters:
            - StripPrefix=1
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 100
                redis-rate-limiter.burstCapacity: 200
```

## 服务间通信

### 通信方式选择

| 场景 | 通信方式 | 技术选型 |
|------|---------|---------|
| **同步调用** | RPC | Spring Cloud OpenFeign |
| **异步消息** | 消息队列 | Kafka |
| **实时推送** | WebSocket | Spring WebSocket |
| **配置同步** | 配置中心 | Nacos Config |
| **服务发现** | 注册中心 | Nacos Discovery |

### 同步调用示例（Feign）

```java
// 秒杀服务调用库存服务
@Service
public class SeckillServiceImpl {

    @Autowired
    private InventoryServiceClient inventoryClient;

    @Autowired
    private OrderProducer orderProducer;

    public Result executeSeckill(SeckillRequest request) {
        // 1. 校验活动
        SeckillActivity activity = validateActivity(request.getActivityId());

        // 2. RPC 调用库存服务扣减库存
        boolean success = inventoryClient.deductStock(
            request.getActivityId(),
            request.getUserId(),
            request.getQuantity()
        );

        if (!success) {
            return Result.error("库存不足");
        }

        // 3. 发送 Kafka 消息创建订单
        orderProducer.send(new SeckillOrderMessage(
            request.getUserId(),
            request.getActivityId(),
            activity.getProductId(),
            activity.getSeckillPrice()
        ));

        return Result.success("秒杀成功，订单生成中");
    }
}
```

### 异步消息示例（Kafka）

```java
// 订单服务消费 Kafka 消息
@Service
public class OrderConsumer {

    @KafkaListener(topics = "seckill-order-topic")
    public void handleSeckillOrder(SeckillOrderMessage msg) {
        try {
            // 1. 生成订单号
            String orderNo = OrderNoGenerator.generate();

            // 2. 创建订单
            Order order = new Order();
            order.setOrderNo(orderNo);
            order.setUserId(msg.getUserId());
            order.setActivityId(msg.getActivityId());
            order.setProductId(msg.getProductId());
            order.setTotalAmount(msg.getPrice());
            order.setExpireTime(LocalDateTime.now().plusMinutes(15));

            orderMapper.insert(order);

            // 3. 推送通知
            notificationClient.pushWebSocket(msg.getUserId(),
                "订单创建成功，订单号：" + orderNo);

        } catch (Exception e) {
            log.error("创建订单失败", e);
            // 回滚库存
            inventoryClient.rollbackStock(
                msg.getActivityId(),
                msg.getOrderNo(),
                msg.getQuantity()
            );
        }
    }
}
```

## 数据一致性方案

### 1. 分布式事务（Seata）

**使用场景**：强一致性要求（如支付）

```java
@GlobalTransactional
public void createOrderWithPayment(OrderDTO orderDTO) {
    // 1. 创建订单
    orderService.createOrder(orderDTO);

    // 2. 扣减库存
    inventoryService.deductStock(orderDTO.getActivityId());

    // 3. 创建支付记录
    paymentService.createPayment(orderDTO.getOrderNo());
}
```

### 2. 最终一致性（Saga 模式）

**秒杀流程**：

```
1. 秒杀服务：扣减 Redis 库存（本地事务）
   ↓ Kafka 消息
2. 订单服务：创建订单（本地事务）
   ↓ 成功 → 结束
   ↓ 失败 → 发送补偿消息
3. 库存服务：回滚库存（补偿事务）
```

### 3. TCC 模式（Try-Confirm-Cancel）

```java
// 库存服务 TCC 实现
@LocalTCC
public interface InventoryTccService {

    @TwoPhaseBusinessAction(name = "deductStock",
                            commitMethod = "commit",
                            rollbackMethod = "rollback")
    boolean deductStock(@BusinessActionContextParameter String activityId,
                        @BusinessActionContextParameter int quantity);

    boolean commit(BusinessActionContext context);

    boolean rollback(BusinessActionContext context);
}
```

### 4. 定时对账

```java
@Scheduled(cron = "0 0 2 * * ?") // 每天凌晨2点
public void reconcileInventory() {
    List<SeckillActivity> activities = activityMapper.selectAll();

    for (SeckillActivity activity : activities) {
        // Redis 库存
        Integer redisStock = redisTemplate.opsForValue().get(
            "stock:activity:" + activity.getId()
        );

        // MySQL 库存
        Integer mysqlStock = inventoryMapper.getAvailableStock(
            activity.getId()
        );

        // 不一致则告警
        if (!Objects.equals(redisStock, mysqlStock)) {
            log.error("库存不一致！ActivityId={}, Redis={}, MySQL={}",
                activity.getId(), redisStock, mysqlStock);
            // 发送告警
            alertService.sendAlert(...);
        }
    }
}
```

## 部署架构

### Kubernetes 部署拓扑

```
┌─────────────────────────────────────────────────────────────────┐
│                        Kubernetes Cluster                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │              Ingress Controller (Nginx)                   │  │
│  │               外部访问入口 + SSL 终止                      │  │
│  └─────────────────────────┬─────────────────────────────────┘  │
│                            │                                     │
│  ┌─────────────────────────┴─────────────────────────────────┐  │
│  │              API Gateway (3 Pods)                         │  │
│  │           路由、鉴权、限流、熔断                           │  │
│  └─────┬───────┬────────┬────────┬─────────┬─────────────────┘  │
│        │       │        │        │         │                     │
│  ┌─────▼──┐ ┌─▼─────┐ ┌▼──────┐ ┌▼──────┐ ┌▼────────┐          │
│  │User Svc│ │Product│ │Seckill│ │Order  │ │Inventory│ ...      │
│  │2 Pods  │ │2 Pods │ │5 Pods │ │3 Pods │ │3 Pods   │          │
│  │        │ │       │ │ (HPA) │ │       │ │         │          │
│  └────────┘ └───────┘ └───────┘ └───────┘ └─────────┘          │
│                            │                                     │
│  ┌────────────────────────┴──────────────────────────────────┐  │
│  │                 StatefulSet 中间件                        │  │
│  │  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐     │  │
│  │  │MySQL    │  │Redis    │  │Kafka    │  │Nacos    │     │  │
│  │  │Master/  │  │Cluster  │  │Cluster  │  │Cluster  │     │  │
│  │  │Slave    │  │6 nodes  │  │3 nodes  │  │3 nodes  │     │  │
│  │  └─────────┘  └─────────┘  └─────────┘  └─────────┘     │  │
│  └────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

### 服务扩缩容策略

```yaml
# 秒杀服务 HPA（高峰期自动扩容）
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: seckill-service-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: seckill-service
  minReplicas: 5
  maxReplicas: 20
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
```

## 技术选型

### Spring Cloud Alibaba 技术栈

| 组件 | 技术选型 | 用途 |
|------|---------|------|
| **注册中心** | Nacos Discovery | 服务注册与发现 |
| **配置中心** | Nacos Config | 动态配置管理 |
| **API 网关** | Spring Cloud Gateway | 统一入口网关 |
| **RPC 调用** | OpenFeign | 声明式 HTTP 客户端 |
| **负载均衡** | Ribbon / LoadBalancer | 客户端负载均衡 |
| **熔断降级** | Sentinel | 流量控制、熔断降级 |
| **分布式事务** | Seata | AT/TCC/Saga 模式 |
| **链路追踪** | SkyWalking | 分布式链路追踪 |
| **消息队列** | Kafka | 异步消息、削峰填谷 |
| **缓存** | Redis Cluster | 分布式缓存 |
| **数据库** | MySQL 8.0 (分库分表) | 关系型数据库 |

### 技术架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                      Spring Cloud Alibaba                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │Nacos Registry│  │Nacos Config  │  │  Sentinel    │          │
│  │服务注册发现   │  │  配置中心    │  │  流控熔断    │          │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
│                                                                   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │            Spring Cloud Gateway (API Gateway)            │   │
│  └──────────────────────────────────────────────────────────┘   │
│                            │                                     │
│  ┌────────────────────────┴──────────────────────────────────┐  │
│  │                  微服务集群                                │  │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐ │  │
│  │  │User Svc  │  │Order Svc │  │Seckill   │  │Inventory │ │  │
│  │  │          │  │          │  │Service   │  │Service   │ │  │
│  │  └──────────┘  └──────────┘  └──────────┘  └──────────┘ │  │
│  └────────────────────────────────────────────────────────────┘  │
│         │               │               │              │          │
│  ┌──────▼───────┐  ┌───▼────┐  ┌──────▼──────┐  ┌───▼──────┐   │
│  │OpenFeign RPC │  │ Kafka  │  │Redis Cluster│  │  MySQL   │   │
│  └──────────────┘  └────────┘  └─────────────┘  └──────────┘   │
│                                                                   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │              SkyWalking (链路追踪 + 监控)                 │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

## 迁移路径

### 阶段 1: 垂直拆分（1-2周）

**目标**：按业务领域拆分成独立服务

```
单体应用
    ↓
拆分为 6 个服务
    - User Service
    - Product Service
    - Seckill Service
    - Order Service
    - Inventory Service
    - Notification Service
```

**步骤**：
1. 引入 Spring Cloud Alibaba 依赖
2. 搭建 Nacos 注册中心和配置中心
3. 提取公共代码到 common 模块
4. 按领域拆分代码到各服务
5. 配置服务间 Feign 调用

---

### 阶段 2: 数据库拆分（1-2周）

**目标**：每个服务独立数据库

```
单一数据库 seckill_db
    ↓
拆分为 6 个数据库
    - user_db
    - product_db
    - seckill_db
    - order_db
    - inventory_db
    - payment_db
```

**注意事项**：
- 使用数据库中间件（ShardingSphere）
- 保留旧表作为备份
- 逐步迁移数据
- 双写验证一致性

---

### 阶段 3: 引入网关和限流（1周）

**目标**：统一入口 + 流量控制

```
添加组件：
    - Spring Cloud Gateway (API 网关)
    - Sentinel (限流熔断)
```

---

### 阶段 4: 异步化改造（1-2周）

**目标**：Kafka 削峰填谷

```
同步调用
    ↓
异步消息
    - 秒杀 → Kafka → 订单
    - 订单 → Kafka → 支付
    - 订单 → Kafka → 通知
```

---

### 阶段 5: 分布式事务（1周）

**目标**：引入 Seata 保证一致性

```
本地事务
    ↓
分布式事务
    - Seata AT 模式（自动补偿）
    - Saga 模式（长事务）
```

---

### 阶段 6: 容器化部署（1-2周）

**目标**：Kubernetes 集群部署

```
VM 部署
    ↓
容器化部署
    - Docker 镜像
    - Kubernetes Deployment
    - HPA 自动伸缩
```

---

### 阶段 7: 监控和链路追踪（1周）

**目标**：可观测性建设

```
添加组件：
    - SkyWalking (链路追踪)
    - Prometheus + Grafana (监控)
    - ELK (日志收集)
```

## 总结

### 微服务拆分收益

✅ **弹性伸缩**：秒杀服务可独立扩容到 20 个实例，其他服务保持 2-3 个
✅ **故障隔离**：订单服务故障不影响秒杀服务
✅ **技术异构**：不同服务可使用不同技术（如库存服务用 Go 重写）
✅ **团队自治**：6 个服务对应 6 个开发小组
✅ **快速迭代**：每个服务独立开发、测试、部署

### 微服务拆分成本

⚠️ **复杂度增加**：从 1 个应用变成 6+ 个服务
⚠️ **运维成本**：需要 Kubernetes、监控、日志、链路追踪
⚠️ **分布式事务**：需要处理数据一致性问题
⚠️ **网络开销**：服务间 RPC 调用增加延迟
⚠️ **学习成本**：团队需要学习微服务技术栈

### 适用场景

- ✅ **大型项目**：用户量 > 100万，QPS > 10万
- ✅ **团队规模**：开发人员 > 20人
- ✅ **业务复杂**：多个独立业务模块
- ❌ **小型项目**：建议使用单体架构或模块化单体
- ❌ **初创公司**：先用单体快速验证，再考虑拆分

---

**微服务不是银弹，根据业务需要谨慎选择！**
