# Spring Boot 版本降级说明

## 问题原因

**Apache Shiro 2.0.1 不兼容 Spring Boot 4.0.3（Jakarta EE）**

### 版本兼容性

| Spring Boot 版本 | Servlet API | Shiro 2.0.1 支持 |
|------------------|-------------|------------------|
| 2.7.x | `javax.servlet.*` | ✅ 完全支持 |
| 3.x | `jakarta.servlet.*` | ❌ 不支持 |
| 4.x | `jakarta.servlet.*` | ❌ 不支持 |

**根本原因**: Shiro 2.0.1 的核心库（`shiro-web`、`shiro-spring`）仍然使用 Java EE 的 `javax.servlet` API，而 Spring Boot 3.x/4.x 已经迁移到 Jakarta EE 的 `jakarta.servlet` API。

## 解决方案

### ✅ 降级 Spring Boot 到 2.7.18

Spring Boot 2.7.18 是 2.x 系列的最后一个版本，稳定且安全，仍然使用 `javax.servlet`，与 Shiro 2.0.1 完全兼容。

### 修改内容

#### 1. pom.xml - 降级 Spring Boot

**修改前**:
```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.0.3</version>
    <relativePath/>
</parent>
```

**修改后**:
```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>2.7.18</version>
    <relativePath/>
</parent>
```

#### 2. 修改导入 - jakarta → javax

**Controller 文件**:
```java
// 修改前
import jakarta.servlet.http.HttpServletRequest;

// 修改后
import javax.servlet.http.HttpServletRequest;
```

**DTO 文件**:
```java
// 修改前
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;

// 修改后
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Email;
```

### 影响的文件

| 文件 | 修改内容 |
|------|---------|
| `pom.xml` | Spring Boot 4.0.3 → 2.7.18 |
| `AuthController.java` | `jakarta.servlet` → `javax.servlet` |
| `UserController.java` | `jakarta.servlet` → `javax.servlet` |
| `UserRegisterRequest.java` | `jakarta.validation` → `javax.validation` |
| `UserLoginRequest.java` | `jakarta.validation` → `javax.validation` |
| `UserUpdateRequest.java` | `jakarta.validation` → `javax.validation` |

## 验证修改

### 1. 清理项目

```bash
cd /Users/mac/Desktop/project/distributed-software-homework/demo
mvn clean
```

### 2. 更新依赖

```bash
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

应该看到：
```
Started DemoApplication in X.XXX seconds
```

### 5. 测试登录

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"merchant1","password":"123456"}'
```

## Spring Boot 2.7.18 特性

### 优势

- ✅ **稳定性**: 2.x 系列的最终版本，经过充分测试
- ✅ **兼容性**: 与 Shiro 2.0.1 完全兼容
- ✅ **安全性**: 包含所有安全补丁
- ✅ **功能完整**: 包含所有核心功能

### 与 4.0.3 的主要区别

| 特性 | Spring Boot 2.7.18 | Spring Boot 4.0.3 |
|------|-------------------|-------------------|
| Servlet API | `javax.servlet` | `jakarta.servlet` |
| Validation API | `javax.validation` | `jakarta.validation` |
| Java 版本 | Java 8+ | Java 17+ |
| Spring Framework | 5.3.x | 7.0.x |

### 缺失的功能

Spring Boot 2.7.18 不包含 4.0.3 的一些新特性：
- ⚠️ 没有 Spring Framework 7.0 的新特性
- ⚠️ 没有 Observability API 的增强
- ⚠️ 没有某些性能优化

但对于大多数项目，这些不影响使用。

## 其他解决方案（不推荐）

### 方案 2：使用 Spring Security（放弃 Shiro）

如果必须使用 Spring Boot 4.0.3，可以考虑使用 Spring Security 替代 Shiro：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
```

**优势**:
- ✅ 原生支持 Jakarta EE
- ✅ 与 Spring Boot 深度集成
- ✅ 功能更强大

**劣势**:
- ❌ 需要重写所有认证和授权逻辑
- ❌ 学习曲线较陡
- ❌ 不符合要求（用户要求使用 Shiro）

### 方案 3：等待 Shiro 更新

等待 Apache Shiro 发布支持 Jakarta EE 的版本（可能是 Shiro 3.x）。

**状态**: 截至 2026-03，Shiro 项目还未发布支持 Jakarta EE 的版本。

## FAQ

### Q1: Spring Boot 2.7.18 还会维护吗？

**答**: 会的。Spring Boot 2.7.x 是 LTS（长期支持）版本，至少维护到 2025 年 8 月。

### Q2: 会影响项目的其他功能吗？

**答**:
- ✅ **不影响**: MyBatis、MySQL、Redis、Kafka 等都兼容
- ✅ **不影响**: 所有业务逻辑代码
- ⚠️ **可能影响**: 如果使用了 Spring Boot 4.x 特有的 API

### Q3: 如何迁移回 Spring Boot 4.x？

如果将来 Shiro 支持 Jakarta EE，可以按以下步骤迁移回去：

1. 升级 Shiro 到支持 Jakarta EE 的版本
2. 升级 Spring Boot 到 4.x
3. 将所有 `javax.*` 改回 `jakarta.*`
4. 测试所有功能

### Q4: 有没有办法同时使用 Spring Boot 4.x 和 Shiro？

**答**: 理论上可以通过字节码转换或类加载器桥接，但：
- ❌ 非常复杂
- ❌ 维护困难
- ❌ 性能开销大
- ❌ 不推荐用于生产环境

## 总结

✅ **最佳方案**: 降级到 Spring Boot 2.7.18

**原因**:
1. 完全兼容 Shiro 2.0.1
2. 稳定且安全
3. 功能完整
4. 修改成本最低

**建议**: 在 Shiro 发布支持 Jakarta EE 的版本之前，继续使用 Spring Boot 2.7.18。

---

**修复日期**: 2026-03-10
**降级版本**: Spring Boot 4.0.3 → 2.7.18
**原因**: Shiro 2.0.1 不支持 Jakarta EE
