# 启动问题修复说明

## 问题描述

启动应用时遇到 Bean 定义冲突错误：

```
The bean 'securityManager', defined in class path resource
[org/apache/shiro/spring/config/web/autoconfigure/ShiroWebAutoConfiguration.class],
could not be registered. A bean with that name has already been defined in
class path resource [com/example/demo/config/ShiroConfig.class]
and overriding is disabled.
```

## 原因分析

Spring Boot 的 Shiro Starter 提供了自动配置类 `ShiroWebAutoConfiguration`，它会自动创建 `securityManager` 等 Bean。

我们在 `ShiroConfig` 中也定义了 `securityManager` Bean，导致 Bean 名称冲突。

## 解决方案

### 方案：禁用 Shiro 自动配置

在 `application.yml` 中禁用 Shiro 的自动配置类，完全使用我们自定义的配置。

**修改文件**: `src/main/resources/application.yml`

```yaml
spring:
  # 禁用自动配置（禁用所有 Shiro 相关的自动配置）
  autoconfigure:
    exclude:
      - org.apache.shiro.spring.boot.autoconfigure.ShiroAutoConfiguration
      - org.apache.shiro.spring.config.web.autoconfigure.ShiroWebAutoConfiguration
      - org.apache.shiro.spring.boot.autoconfigure.ShiroAnnotationProcessorAutoConfiguration
```

### 相关修改

#### 1. 移除自动配置相关的配置项

由于禁用了自动配置，以下配置项不再需要：

```yaml
# 已移除
shiro:
  enabled: true
  web:
    enabled: true
  session-manager:
    session-validation-interval: 1800000
    global-session-timeout: 1800000
  cookie:
    name: SHIROSESSIONID
    max-age: 1800
    http-only: true
```

这些配置是给自动配置使用的，我们现在完全通过 `ShiroConfig` 来配置。

#### 2. 完善 ShiroConfig 配置

**添加注解支持**：

```java
@Bean
public AuthorizationAttributeSourceAdvisor authorizationAttributeSourceAdvisor(SecurityManager securityManager) {
    AuthorizationAttributeSourceAdvisor advisor = new AuthorizationAttributeSourceAdvisor();
    advisor.setSecurityManager(securityManager);
    return advisor;
}

@Bean
public DefaultAdvisorAutoProxyCreator defaultAdvisorAutoProxyCreator() {
    DefaultAdvisorAutoProxyCreator proxyCreator = new DefaultAdvisorAutoProxyCreator();
    proxyCreator.setProxyTargetClass(true);
    return proxyCreator;
}
```

这样 `@RequiresRoles` 和 `@RequiresPermissions` 注解才能生效。

**更新过滤规则**：

```java
// 公开接口
filterChainDefinitionMap.put("/api/auth/register/**", "anon");
filterChainDefinitionMap.put("/api/auth/login", "anon");

// 需要认证的接口
filterChainDefinitionMap.put("/api/auth/logout", "authc");
filterChainDefinitionMap.put("/api/auth/info", "authc");
filterChainDefinitionMap.put("/api/auth/change-password", "authc");

// 测试接口
filterChainDefinitionMap.put("/api/auth/test/**", "authc");
```

## 修改文件清单

### 修改的文件
- ✅ `src/main/resources/application.yml` - 禁用自动配置、移除 Shiro 配置项
- ✅ `src/main/java/com/example/demo/config/ShiroConfig.java` - 添加注解支持、更新过滤规则

### 配置完整性检查

我们的 `ShiroConfig` 现在包含以下 Bean：

1. ✅ `ShiroFilterFactoryBean` - 过滤器工厂
2. ✅ `SecurityManager` - 安全管理器
3. ✅ `UserRealm` - 自定义 Realm
4. ✅ `CustomCredentialsMatcher` - 自定义密码匹配器
5. ✅ `EhCacheManager` - 缓存管理器
6. ✅ `AuthorizationAttributeSourceAdvisor` - 注解支持
7. ✅ `DefaultAdvisorAutoProxyCreator` - AOP 代理

## 验证启动

### 1. 清理并重新编译

```bash
cd /Users/mac/Desktop/project/distributed-software-homework/demo

# 清理
mvn clean

# 编译
mvn compile
```

### 2. 启动应用

```bash
mvn spring-boot:run
```

或在 IDE 中运行 `DemoApplication`。

### 3. 验证启动成功

**检查日志**：

应该看到类似以下的日志（没有错误）：

```
Started DemoApplication in X.XXX seconds
```

**测试健康检查**：

```bash
curl http://localhost:8080/ping
```

应返回：
```json
{"code":200,"message":"success","data":"pong","timestamp":...}
```

## 完整的 application.yml 配置

```yaml
server:
  port: 8080

spring:
  application:
    name: seckill-system

  # 禁用自动配置
  autoconfigure:
    exclude:
      - org.apache.shiro.spring.boot.autoconfigure.ShiroAutoConfiguration
      - org.apache.shiro.spring.config.web.autoconfigure.ShiroWebAutoConfiguration

  # MySQL 数据源
  datasource:
    url: jdbc:mysql://localhost:3306/distributed?...
    username: root
    password: wepiePro
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5

  # Redis
  data:
    redis:
      host: localhost
      port: 6379
      password:
      database: 0

  # SQL init
  sql:
    init:
      mode: always
      schema-locations: classpath:sql/schema.sql

# MyBatis
mybatis:
  mapper-locations: classpath:mapper/*.xml
  type-aliases-package: com.example.demo.model.entity
  configuration:
    map-underscore-to-camel-case: true

# Logging
logging:
  level:
    com.example.demo: DEBUG
    org.apache.shiro: DEBUG
```

## 常见问题

### Q1: 还是报 Bean 冲突错误

**解决**：
1. 确认 `application.yml` 中的 `exclude` 配置正确
2. 清理项目：`mvn clean`
3. 重新编译：`mvn compile`
4. 重启 IDE

### Q2: 注解不生效（@RequiresRoles、@RequiresPermissions）

**解决**：
1. 确认 `ShiroConfig` 中有 `AuthorizationAttributeSourceAdvisor` Bean
2. 确认 `ShiroConfig` 中有 `DefaultAdvisorAutoProxyCreator` Bean
3. 检查过滤规则是否覆盖了注解配置

### Q3: Session 超时设置

如果需要自定义 Session 超时时间，在 `ShiroConfig` 中配置 `SessionManager`：

```java
@Bean
public SessionManager sessionManager() {
    DefaultWebSessionManager sessionManager = new DefaultWebSessionManager();
    sessionManager.setGlobalSessionTimeout(1800000); // 30分钟
    return sessionManager;
}

// 在 SecurityManager 中设置
@Bean
public SecurityManager securityManager(UserRealm userRealm,
                                      EhCacheManager ehCacheManager,
                                      SessionManager sessionManager) {
    DefaultWebSecurityManager securityManager = new DefaultWebSecurityManager();
    securityManager.setRealm(userRealm);
    securityManager.setCacheManager(ehCacheManager);
    securityManager.setSessionManager(sessionManager);
    return securityManager;
}
```

## 测试登录

启动成功后，测试登录功能：

```bash
# 商家登录
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -c cookies.txt \
  -d '{"username":"merchant1","password":"123456"}'

# 获取用户信息
curl http://localhost:8080/api/auth/info -b cookies.txt

# 测试商家权限
curl http://localhost:8080/api/auth/test/merchant -b cookies.txt
```

## 总结

✅ **问题**: Bean 定义冲突
✅ **原因**: Shiro 自动配置与自定义配置冲突
✅ **解决**: 禁用自动配置，使用完全自定义配置
✅ **优化**: 添加注解支持、完善过滤规则

现在应用应该可以正常启动了！

---

**修复日期**: 2026-03-10
**问题类型**: Bean 定义冲突
**解决方式**: 禁用 Shiro 自动配置
