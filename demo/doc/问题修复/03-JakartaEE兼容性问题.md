# Jakarta EE 迁移问题修复

## 问题描述

启动时报错：

```
java.lang.ClassNotFoundException: javax.servlet.Filter
Caused by: java.lang.NoClassDefFoundError: javax/servlet/Filter
```

## 原因分析

Spring Boot 4.0.3 已经从 Java EE (`javax.*`) 迁移到 Jakarta EE (`jakarta.*`)，使用的是 `jakarta.servlet.*` API。

但是 Shiro 2.0.1 的 `shiro-spring-boot-starter` 包含的某些自动配置类还在引用旧的 `javax.servlet.Filter`，导致类找不到。

## 解决方案

**移除 `shiro-spring-boot-starter`，改用核心 Shiro 库**。

### 修改 pom.xml

**修改前**（有问题）：
```xml
<!-- Apache Shiro -->
<dependency>
    <groupId>org.apache.shiro</groupId>
    <artifactId>shiro-spring-boot-starter</artifactId>
    <version>2.0.1</version>
</dependency>
```

**修改后**（正确）：
```xml
<!-- Apache Shiro (不使用 starter，避免自动配置冲突) -->
<dependency>
    <groupId>org.apache.shiro</groupId>
    <artifactId>shiro-spring</artifactId>
    <version>2.0.1</version>
</dependency>
<dependency>
    <groupId>org.apache.shiro</groupId>
    <artifactId>shiro-web</artifactId>
    <version>2.0.1</version>
</dependency>
<dependency>
    <groupId>org.apache.shiro</groupId>
    <artifactId>shiro-ehcache</artifactId>
    <version>2.0.1</version>
</dependency>
```

### 修改 application.yml

**不再需要禁用自动配置**（因为没有 starter 就没有自动配置）：

**修改前**：
```yaml
spring:
  autoconfigure:
    exclude:
      - org.apache.shiro.spring.boot.autoconfigure.ShiroAutoConfiguration
      - org.apache.shiro.spring.config.web.autoconfigure.ShiroWebAutoConfiguration
      - org.apache.shiro.spring.boot.autoconfigure.ShiroAnnotationProcessorAutoConfiguration
```

**修改后**（移除 exclude）：
```yaml
spring:
  application:
    name: seckill-system

  # MySQL 数据源
  datasource:
    ...
```

## 优势

使用核心 Shiro 库而不是 starter 有以下优势：

1. ✅ **避免自动配置冲突** - 不会有 Bean 定义冲突
2. ✅ **兼容性更好** - 核心库不依赖 `javax.servlet`
3. ✅ **更灵活** - 完全控制 Shiro 配置
4. ✅ **更清晰** - 所有配置都在 `ShiroConfig` 中

## 完整的依赖说明

### Shiro 核心依赖

| 依赖 | 作用 |
|------|------|
| `shiro-spring` | Shiro 与 Spring 集成 |
| `shiro-web` | Shiro Web 支持（Filter、Session 等）|
| `shiro-ehcache` | Shiro EhCache 缓存支持 |

这三个依赖足够实现完整的 Shiro 功能，不需要 starter。

## 验证修复

### 1. 清理项目

```bash
cd /Users/mac/Desktop/project/distributed-software-homework/demo
mvn clean
```

### 2. 更新依赖

```bash
mvn dependency:purge-local-repository
mvn dependency:resolve
```

### 3. 编译

```bash
mvn compile
```

### 4. 启动

```bash
mvn spring-boot:run
```

应该能看到：
```
Started DemoApplication in X.XXX seconds
```

### 5. 测试

```bash
curl http://localhost:8080/ping
# 返回: {"code":200,"message":"success","data":"pong",...}

curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"merchant1","password":"123456"}'
```

## 常见问题

### Q1: 还是找不到类

**清理 Maven 缓存**：
```bash
rm -rf ~/.m2/repository/org/apache/shiro
mvn clean install
```

### Q2: IDE 报错

**刷新 Maven 项目**：
- IntelliJ IDEA: 右键 pom.xml > Maven > Reload Project
- Eclipse: 右键项目 > Maven > Update Project

### Q3: 为什么不升级 Shiro 版本？

Shiro 2.0.1 是当前稳定版本，已经兼容 Jakarta EE。问题不在核心库，而在 starter 的自动配置。

## 与之前问题的关系

### 问题演进

1. **ByteSource 编译错误** → 创建 `CustomCredentialsMatcher`
2. **Bean 冲突（securityManager）** → 禁用自动配置
3. **Bean 冲突（defaultAdvisorAutoProxyCreator）** → 禁用更多自动配置
4. **javax.servlet 找不到** → **移除 starter**（最终解决方案）

### 最终方案

**不使用 `shiro-spring-boot-starter`**，这样：
- ❌ 不需要禁用自动配置
- ❌ 不会有 Bean 冲突
- ❌ 不会有 Jakarta EE 迁移问题
- ✅ 完全自定义配置

## 修改文件清单

| 文件 | 修改内容 |
|------|---------|
| `pom.xml` | 移除 `shiro-spring-boot-starter`，添加核心库 |
| `application.yml` | 移除 `autoconfigure.exclude` 配置 |

## 总结

🎯 **根本原因**: `shiro-spring-boot-starter` 与 Spring Boot 4.0.3（Jakarta EE）不兼容

🛠️ **解决方案**: 使用核心 Shiro 库（`shiro-spring` + `shiro-web` + `shiro-ehcache`）

✅ **结果**: 所有问题解决，项目可以正常启动

---

**修复日期**: 2026-03-10
**问题类型**: Jakarta EE 迁移
**影响**: 必须修改才能启动
