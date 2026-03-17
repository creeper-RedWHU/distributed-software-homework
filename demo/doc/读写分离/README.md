# MySQL 读写分离

## 一、架构概述

```
                    ┌──────────────┐
                    │  Application │
                    └──────┬───────┘
                           │
                    ┌──────▼───────┐
                    │ RoutingData  │
                    │   Source     │
                    └──┬───────┬───┘
                       │       │
              写操作    │       │   读操作(@ReadOnly)
              ┌────────▼┐   ┌──▼────────┐
              │  Master  │   │   Slave   │
              │  :3306   │──►│   :3307   │
              │  读写    │复制│   只读    │
              └──────────┘   └───────────┘
```

- **写操作**：默认路由到 Master
- **读操作**：标记 `@ReadOnly` 注解的方法路由到 Slave
- **复制**：MySQL GTID 主从复制，Master 的数据自动同步到 Slave

## 二、核心实现

### 2.1 关键类

| 类 | 作用 |
|---|------|
| `RoutingDataSource` | 继承 `AbstractRoutingDataSource`，根据 ThreadLocal 切换数据源 |
| `DataSourceContextHolder` | ThreadLocal 持有当前线程使用的数据源 key |
| `DataSourceConfig` | 配置 Master/Slave 两个 HikariDataSource + RoutingDataSource |
| `@ReadOnly` | 自定义注解，标记方法使用从库 |
| `ReadWriteAspect` | AOP 切面，拦截 @ReadOnly 注解自动切换到从库 |

### 2.2 RoutingDataSource 原理

```java
public class RoutingDataSource extends AbstractRoutingDataSource {
    @Override
    protected Object determineCurrentLookupKey() {
        return DataSourceContextHolder.getDataSource(); // "master" 或 "slave"
    }
}
```

Spring 每次获取数据库连接时，都会调用 `determineCurrentLookupKey()` 来决定使用哪个数据源。

### 2.3 AOP 切面

```java
@Around("@annotation(readOnly)")
public Object aroundReadOnly(ProceedingJoinPoint joinPoint, ReadOnly readOnly) throws Throwable {
    try {
        DataSourceContextHolder.useSlave();  // 切换到从库
        return joinPoint.proceed();
    } finally {
        DataSourceContextHolder.clear();     // 清理，恢复默认(主库)
    }
}
```

### 2.4 使用方式

```java
// 写操作 - 默认走主库，无需注解
public void createProduct(Product product) {
    productMapper.insert(product);
}

// 读操作 - 加 @ReadOnly 注解走从库
@ReadOnly
public Product getProduct(Long id) {
    return productMapper.selectById(id);
}
```

## 三、Docker 部署

### 3.1 启动完整环境

```bash
cd demo
docker-compose -f docker-compose.full.yml up -d --build
```

### 3.2 MySQL 主从复制配置

**Master (`docker/mysql/master/my.cnf`):**
```ini
[mysqld]
server-id=1
log-bin=mysql-bin
binlog-format=ROW
gtid_mode=ON
enforce-gtid-consistency=ON
binlog-do-db=seckill_db
```

**Slave (`docker/mysql/slave/my.cnf`):**
```ini
[mysqld]
server-id=2
relay-log=relay-log
read-only=1
gtid_mode=ON
enforce-gtid-consistency=ON
```

### 3.3 验证主从复制

```bash
# 查看从库复制状态
docker exec seckill-mysql-slave mysql -uroot -proot -e "SHOW SLAVE STATUS\G" | grep -E "Slave_IO_Running|Slave_SQL_Running|Seconds_Behind_Master"
```

预期输出：
```
Slave_IO_Running: Yes
Slave_SQL_Running: Yes
Seconds_Behind_Master: 0
```

## 四、测试读写分离

### 4.1 测试接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/rw-test/write` | POST | 写入数据（走主库） |
| `/api/rw-test/read/{id}` | GET | 读取数据（走从库，@ReadOnly） |
| `/api/rw-test/write-then-read` | POST | 先写后读（验证主从延迟） |
| `/api/rw-test/status` | GET | 查看当前数据源状态 |

### 4.2 测试步骤

```bash
# 1. 写入数据（使用主库）
curl -X POST "http://localhost/api/rw-test/write?name=测试商品&price=199.99"
# 返回: datasource=master

# 2. 读取数据（使用从库）
curl http://localhost/api/rw-test/read/1
# 返回: datasource=slave

# 3. 先写后读
curl -X POST http://localhost/api/rw-test/write-then-read
# 返回: write_datasource=master, read_datasource=slave

# 4. 查看后端日志验证
docker logs seckill-app1 2>&1 | grep "切换数据源"
```

### 4.3 验证方法

查看应用日志中的数据源切换记录：
```
切换数据源: slave     ← 读操作路由到从库
切换数据源: master    ← 写操作路由到主库
```

查看 HikariCP 连接池使用情况：
```bash
# master-pool 处理写请求
# slave-pool 处理读请求
docker logs seckill-app1 2>&1 | grep "pool"
```

## 五、配置说明

### application.yml

```yaml
spring:
  datasource:
    master:
      url: jdbc:mysql://localhost:3306/seckill_db?...
      username: root
      password: root
    slave:
      url: jdbc:mysql://localhost:3307/seckill_db?...
      username: root
      password: root
```

### Docker 环境变量

```yaml
SPRING_DATASOURCE_MASTER_URL: jdbc:mysql://mysql-master:3306/seckill_db?...
SPRING_DATASOURCE_SLAVE_URL: jdbc:mysql://mysql-slave:3306/seckill_db?...
```

## 六、注意事项

1. **主从延迟**：从库数据可能有毫秒级延迟，写入后立即读取可能读到旧数据
2. **事务内不切换**：如果方法在事务中，数据源在事务开始时就确定了，中途不会切换
3. **本地开发**：master 和 slave 可以指向同一个数据库，功能正常但无实际分离效果
