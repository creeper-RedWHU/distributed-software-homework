# Apache Shiro 原理与实现详解

## 📚 目录

1. [Shiro 核心概念](#shiro-核心概念)
2. [Shiro 工作原理](#shiro-工作原理)
3. [认证流程详解](#认证流程详解)
4. [授权流程详解](#授权流程详解)
5. [本项目实现方案](#本项目实现方案)
6. [关键代码解析](#关键代码解析)
7. [架构设计图](#架构设计图)
8. [完整执行流程](#完整执行流程)

---

## Shiro 核心概念

### 1. 四大核心组件

Apache Shiro 有四个核心概念：

```
┌─────────────────────────────────────────────────────┐
│                   Subject (主体)                      │
│              当前与系统交互的用户/进程                   │
└────────────────────┬────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────┐
│              SecurityManager (安全管理器)             │
│         Shiro的核心，管理所有Subject的安全操作          │
└────────────────────┬────────────────────────────────┘
                     │
          ┌──────────┴──────────┐
          ▼                     ▼
┌──────────────────┐   ┌──────────────────┐
│  Realm (领域)     │   │  SessionManager  │
│  数据源连接器      │   │  Session管理器    │
└──────────────────┘   └──────────────────┘
```

#### (1) Subject - 主体

**概念**: 代表当前与系统交互的实体（用户、程序等）

**本质**: 是一个门面（Facade），背后真正干活的是 SecurityManager

**常用操作**:
```java
Subject subject = SecurityUtils.getSubject();

// 认证
subject.login(token);
subject.logout();
subject.isAuthenticated();

// 授权
subject.hasRole("MERCHANT");
subject.isPermitted("product:create");

// 获取信息
String username = (String) subject.getPrincipal();
Session session = subject.getSession();
```

#### (2) SecurityManager - 安全管理器

**概念**: Shiro 的核心，负责协调所有安全相关的操作

**职责**:
- 认证（Authentication）- 验证用户身份
- 授权（Authorization）- 验证用户权限
- 会话管理（Session Management）
- 缓存管理（Cache Management）

**本项目配置**:
```java
@Bean
public SecurityManager securityManager(UserRealm userRealm,
                                      EhCacheManager ehCacheManager) {
    DefaultWebSecurityManager securityManager = new DefaultWebSecurityManager();
    securityManager.setRealm(userRealm);           // 设置 Realm
    securityManager.setCacheManager(ehCacheManager); // 设置缓存
    return securityManager;
}
```

#### (3) Realm - 领域

**概念**: 数据源的连接器，负责从数据源获取认证和授权数据

**核心方法**:
- `doGetAuthenticationInfo()` - 获取认证信息
- `doGetAuthorizationInfo()` - 获取授权信息

**本质**: Realm 是你的应用与 Shiro 之间的桥梁

#### (4) SessionManager - 会话管理器

**概念**: 管理用户的 Session 生命周期

**功能**:
- Session 创建、销毁
- Session 超时管理
- Session 持久化

---

## Shiro 工作原理

### 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                        Application Code                       │
│                       (业务代码 - Controller)                  │
└────────────────────────────┬────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────┐
│                    Subject (SecurityUtils)                    │
│                  门面模式 - 提供简单的API                       │
└────────────────────────────┬────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────┐
│                      SecurityManager                          │
│                     (协调所有安全操作)                          │
├─────────────┬───────────────┬──────────────┬────────────────┤
│ Authenticator│ Authorizer   │SessionManager│ CacheManager   │
└──────┬───────┴───────┬───────┴──────┬───────┴─────┬──────────┘
       │               │              │             │
       ▼               ▼              ▼             ▼
┌────────────┐  ┌────────────┐  ┌─────────┐  ┌──────────┐
│   Realm    │  │   Realm    │  │ Session │  │  Cache   │
│(认证数据源) │  │(授权数据源) │  │ (会话)  │  │  (缓存)  │
└─────┬──────┘  └─────┬──────┘  └─────────┘  └──────────┘
      │               │
      ▼               ▼
┌─────────────────────────────┐
│        Database / LDAP       │
│      (用户、角色、权限数据)    │
└─────────────────────────────┘
```

### 核心流程

1. **应用调用 Subject 方法**
2. **Subject 委托给 SecurityManager**
3. **SecurityManager 调用 Realm 获取数据**
4. **Realm 从数据库查询数据并返回**
5. **SecurityManager 处理结果**
6. **返回结果给应用**

---

## 认证流程详解

### 认证（Authentication）概念

**认证**: 验证用户身份，通常是验证用户名和密码

### 完整认证流程

```
┌─────────────┐
│   用户登录   │
│ (输入账号密码)│
└──────┬──────┘
       │
       ▼
┌──────────────────────────────────────────┐
│ 1. Controller 接收请求                    │
│    AuthController.login()                │
│    - 获取 username 和 password            │
└──────┬───────────────────────────────────┘
       │
       ▼
┌──────────────────────────────────────────┐
│ 2. AuthService 处理登录                   │
│    - 创建 UsernamePasswordToken           │
│    - Subject.login(token)                │
└──────┬───────────────────────────────────┘
       │
       ▼
┌──────────────────────────────────────────┐
│ 3. SecurityManager 处理认证               │
│    - 调用 Authenticator                   │
│    - Authenticator 调用 Realm             │
└──────┬───────────────────────────────────┘
       │
       ▼
┌──────────────────────────────────────────┐
│ 4. UserRealm.doGetAuthenticationInfo()   │
│    ① 根据 username 查询用户登录信息        │
│    ② 检查账号状态（是否禁用）              │
│    ③ 返回 AuthenticationInfo              │
│       - principal: 用户名                 │
│       - credentials: 数据库密码            │
└──────┬───────────────────────────────────┘
       │
       ▼
┌──────────────────────────────────────────┐
│ 5. CustomCredentialsMatcher 验证密码      │
│    ① 获取用户输入的密码                    │
│    ② 从数据库获取盐值                      │
│    ③ 使用 PasswordUtil 加密并比对         │
│    ④ 返回匹配结果（true/false）           │
└──────┬───────────────────────────────────┘
       │
       ├─ 成功 ─────────────────────────────┐
       │                                     │
       │                                     ▼
       │                        ┌──────────────────┐
       │                        │ 6. 创建 Session   │
       │                        │ 7. 返回登录信息   │
       │                        └──────────────────┘
       │
       └─ 失败 ─────────────────────────────┐
                                            │
                                            ▼
                               ┌──────────────────────┐
                               │ 抛出认证异常:          │
                               │ - UnknownAccount     │
                               │ - IncorrectCredentials│
                               │ - LockedAccount      │
                               └──────────────────────┘
```

### 本项目认证实现

#### 第一步：Controller 接收登录请求

```java
@PostMapping("/login")
public Result<UserLoginVO> login(@Validated @RequestBody UserLoginRequest request,
                                  HttpServletRequest httpRequest) {
    String loginIp = getClientIp(httpRequest);
    return authService.login(request, loginIp);
}
```

#### 第二步：Service 创建 Token 并调用 Shiro

```java
public Result<UserLoginVO> login(UserLoginRequest request, String loginIp) {
    // 1. 获取 Subject
    Subject subject = SecurityUtils.getSubject();

    // 2. 创建认证 Token
    UsernamePasswordToken token = new UsernamePasswordToken(
        request.getUsername(),
        request.getPassword()
    );

    try {
        // 3. 执行登录（会调用 UserRealm）
        subject.login(token);

        // 4. 登录成功，获取用户信息
        String username = (String) subject.getPrincipal();
        // ... 返回登录信息

    } catch (UnknownAccountException e) {
        return Result.fail(ErrorCode.USER_NOT_FOUND);
    } catch (IncorrectCredentialsException e) {
        return Result.fail(ErrorCode.PASSWORD_ERROR);
    } catch (LockedAccountException e) {
        return Result.fail(ErrorCode.USER_DISABLED);
    }
}
```

#### 第三步：Realm 获取认证信息

```java
@Override
protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token)
        throws AuthenticationException {

    UsernamePasswordToken upToken = (UsernamePasswordToken) token;
    String username = upToken.getUsername();

    // 1. 查询用户登录信息
    UserLogin userLogin = userLoginMapper.selectByUsername(username);
    if (userLogin == null) {
        throw new UnknownAccountException("用户不存在");
    }

    // 2. 检查账号状态
    if (userLogin.getStatus() == 0) {
        throw new LockedAccountException("账号已被禁用");
    }

    // 3. 返回认证信息（Shiro 会自动验证密码）
    return new SimpleAuthenticationInfo(
        username,                   // principal: 身份信息
        userLogin.getPassword(),    // credentials: 密码
        getName()                   // realmName
    );
}
```

#### 第四步：自定义密码匹配器

```java
@Override
public boolean doCredentialsMatch(AuthenticationToken token, AuthenticationInfo info) {
    UsernamePasswordToken upToken = (UsernamePasswordToken) token;

    // 1. 获取用户输入的密码
    String inputPassword = new String(upToken.getPassword());

    // 2. 获取数据库中的密码
    String storedPassword = (String) info.getCredentials();

    // 3. 从数据库获取盐值
    String username = upToken.getUsername();
    UserLogin userLogin = userLoginMapper.selectByUsername(username);
    String salt = userLogin.getSalt();

    // 4. 使用 SHA-256 + Salt 验证
    return PasswordUtil.verifyPassword(inputPassword, storedPassword, salt);
}
```

### 认证关键点

1. **Token**: 用户提交的认证信息（用户名、密码）
2. **AuthenticationInfo**: Realm 返回的数据库信息
3. **CredentialsMatcher**: 负责比对 Token 和 AuthenticationInfo

---

## 授权流程详解

### 授权（Authorization）概念

**授权**: 验证用户是否有权限执行某个操作

### 完整授权流程

```
┌─────────────┐
│ 用户访问接口  │
│ (需要权限)   │
└──────┬──────┘
       │
       ▼
┌──────────────────────────────────────────┐
│ 1. Shiro 拦截器检查权限                    │
│    - ShiroFilter 拦截请求                 │
│    - 检查是否需要认证/授权                 │
└──────┬───────────────────────────────────┘
       │
       ▼
┌──────────────────────────────────────────┐
│ 2. 或者注解触发权限检查                    │
│    - @RequiresRoles("MERCHANT")          │
│    - @RequiresPermissions("product:create")│
└──────┬───────────────────────────────────┘
       │
       ▼
┌──────────────────────────────────────────┐
│ 3. SecurityManager 处理授权               │
│    - 调用 Authorizer                      │
│    - Authorizer 调用 Realm                │
└──────┬───────────────────────────────────┘
       │
       ▼
┌──────────────────────────────────────────┐
│ 4. UserRealm.doGetAuthorizationInfo()    │
│    ① 获取当前用户名                        │
│    ② 查询用户登录信息获取 userType         │
│    ③ 根据 userType 分配角色               │
│    ④ 根据角色分配权限                      │
│    ⑤ 返回 AuthorizationInfo               │
└──────┬───────────────────────────────────┘
       │
       ▼
┌──────────────────────────────────────────┐
│ 5. SecurityManager 检查权限               │
│    - 比对用户的角色/权限                   │
│    - 判断是否有权访问                      │
└──────┬───────────────────────────────────┘
       │
       ├─ 有权限 ──────────────────────────┐
       │                                   │
       │                                   ▼
       │                      ┌──────────────────┐
       │                      │ 6. 执行业务逻辑   │
       │                      └──────────────────┘
       │
       └─ 无权限 ──────────────────────────┐
                                           │
                                           ▼
                              ┌──────────────────────┐
                              │ 返回 403 Forbidden    │
                              └──────────────────────┘
```

### 本项目授权实现

#### 第一步：配置过滤规则（URL 级别）

```java
@Bean
public ShiroFilterFactoryBean shiroFilter(SecurityManager securityManager) {
    ShiroFilterFactoryBean filterBean = new ShiroFilterFactoryBean();
    filterBean.setSecurityManager(securityManager);

    Map<String, String> filterChainDefinitionMap = new LinkedHashMap<>();

    // 公开接口
    filterChainDefinitionMap.put("/api/auth/login", "anon");

    // 需要认证
    filterChainDefinitionMap.put("/api/auth/logout", "authc");

    // 需要角色
    filterChainDefinitionMap.put("/api/merchant/**", "authc,roles[MERCHANT]");
    filterChainDefinitionMap.put("/api/buyer/**", "authc,roles[BUYER]");
    filterChainDefinitionMap.put("/api/admin/**", "authc,roles[ADMIN]");

    // 需要权限
    filterChainDefinitionMap.put("/api/product/create", "authc,perms[product:create]");

    filterBean.setFilterChainDefinitionMap(filterChainDefinitionMap);
    return filterBean;
}
```

#### 第二步：使用注解（方法级别）

```java
// 需要商家角色
@GetMapping("/test/merchant")
@RequiresRoles("MERCHANT")
public Result<String> testMerchant() {
    return Result.success("商家权限测试成功");
}

// 需要创建商品权限
@PostMapping("/product/create")
@RequiresPermissions("product:create")
public Result<String> createProduct() {
    return Result.success("创建商品成功");
}

// 需要多个权限
@RequiresPermissions(value = {"product:view", "product:create"}, logical = Logical.AND)
public Result<String> viewAndCreate() {
    return Result.success("同时拥有查看和创建权限");
}
```

#### 第三步：Realm 分配权限

```java
@Override
protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
    // 1. 获取用户名
    String username = (String) principals.getPrimaryPrincipal();

    // 2. 查询用户登录信息
    UserLogin userLogin = userLoginMapper.selectByUsername(username);

    SimpleAuthorizationInfo info = new SimpleAuthorizationInfo();

    // 3. 添加角色
    Set<String> roles = new HashSet<>();
    roles.add(userLogin.getUserType()); // MERCHANT, BUYER, ADMIN
    info.setRoles(roles);

    // 4. 根据角色添加权限
    Set<String> permissions = new HashSet<>();
    switch (userLogin.getUserType()) {
        case "ADMIN":
            permissions.add("user:*");
            permissions.add("product:*");
            permissions.add("order:*");
            permissions.add("system:*");
            break;
        case "MERCHANT":
            permissions.add("product:create");
            permissions.add("product:update");
            permissions.add("product:delete");
            permissions.add("product:view");
            permissions.add("order:view");
            permissions.add("order:process");
            break;
        case "BUYER":
            permissions.add("product:view");
            permissions.add("order:create");
            permissions.add("order:view");
            permissions.add("order:cancel");
            break;
    }
    info.setStringPermissions(permissions);

    return info;
}
```

### 授权关键点

1. **角色（Role）**: 用户的身份标识（MERCHANT、BUYER、ADMIN）
2. **权限（Permission）**: 具体的操作权限（product:create、order:view）
3. **授权方式**:
   - URL 过滤器（粗粒度）
   - 方法注解（细粒度）
   - 编程式检查（灵活）

---

## 本项目实现方案

### 技术选型

| 组件 | 选择 | 原因 |
|------|------|------|
| 认证框架 | Apache Shiro 2.0.1 | 轻量级、易于集成 |
| Spring Boot | 2.7.18 | 兼容 Shiro（不支持 Jakarta EE）|
| 密码加密 | SHA-256 + Salt | 安全性高、不可逆 |
| Session 存储 | EhCache | 内存缓存、快速访问 |
| 权限模型 | RBAC | 基于角色的访问控制 |

### 核心设计

#### 1. 双表设计

```
┌─────────────────┐          ┌─────────────────────┐
│   t_user        │          │   t_user_login      │
│ (用户信息表)     │          │   (用户登录表)       │
├─────────────────┤          ├─────────────────────┤
│ id              │◄────┐    │ id                  │
│ username        │     └────│ user_id (FK)        │
│ nickname        │          │ username (unique)   │
│ phone           │          │ password (encrypted)│
│ email           │          │ user_type           │
│ status          │          │   - MERCHANT        │
│ last_login_time │          │   - BUYER           │
│ last_login_ip   │          │   - ADMIN           │
│ ...             │          │ salt                │
└─────────────────┘          │ status              │
                             └─────────────────────┘
```

**设计理由**:
- **t_user**: 存储用户基本信息（业务数据）
- **t_user_login**: 存储登录凭证（安全数据）
- **分离优势**: 安全性高、职责清晰、便于扩展

#### 2. 用户类型与权限映射

```
MERCHANT (商家)
  └─ Roles: [MERCHANT]
  └─ Permissions:
      - product:create
      - product:update
      - product:delete
      - product:view
      - order:view
      - order:process

BUYER (买家)
  └─ Roles: [BUYER]
  └─ Permissions:
      - product:view
      - order:create
      - order:view
      - order:cancel

ADMIN (管理员)
  └─ Roles: [ADMIN]
  └─ Permissions:
      - user:*
      - product:*
      - order:*
      - system:*
```

#### 3. 密码加密方案

```
原始密码: "123456"
    ↓
生成随机盐值: "salt1merchant1234567890abcdefghij"
    ↓
SHA-256(密码 + 盐值) × 1024次迭代
    ↓
加密结果: "30f8e16932e191d7be1b9d7a163dd8596b1ce7706090143e559e282eff2d3f2c"
    ↓
存储: password + salt 分别保存
```

**安全特性**:
- **SHA-256**: 256位哈希算法
- **盐值**: 每个用户独立，防止彩虹表攻击
- **1024次迭代**: 增加暴力破解成本
- **不可逆**: 无法从密文还原明文

---

## 关键代码解析

### 1. UserRealm - 认证授权核心

```java
public class UserRealm extends AuthorizingRealm {

    private final UserLoginMapper userLoginMapper;
    private final UserMapper userMapper;

    // 构造函数注入（避免循环依赖）
    public UserRealm(UserLoginMapper userLoginMapper, UserMapper userMapper) {
        this.userLoginMapper = userLoginMapper;
        this.userMapper = userMapper;
    }

    /**
     * 授权：当访问需要权限的资源时调用
     */
    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        String username = (String) principals.getPrimaryPrincipal();
        UserLogin userLogin = userLoginMapper.selectByUsername(username);

        SimpleAuthorizationInfo info = new SimpleAuthorizationInfo();

        // 设置角色
        info.addRole(userLogin.getUserType());

        // 设置权限
        info.addStringPermissions(getPermissionsByType(userLogin.getUserType()));

        return info;
    }

    /**
     * 认证：当用户登录时调用
     */
    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token)
            throws AuthenticationException {

        UsernamePasswordToken upToken = (UsernamePasswordToken) token;
        String username = upToken.getUsername();

        // 1. 查询用户
        UserLogin userLogin = userLoginMapper.selectByUsername(username);
        if (userLogin == null) {
            throw new UnknownAccountException("用户不存在");
        }

        // 2. 检查状态
        if (userLogin.getStatus() == 0) {
            throw new LockedAccountException("账号已被禁用");
        }

        // 3. 返回认证信息
        return new SimpleAuthenticationInfo(
            username,
            userLogin.getPassword(),
            getName()
        );
    }
}
```

**关键点**:
- `doGetAuthenticationInfo`: 只负责返回数据，不负责密码验证
- `doGetAuthorizationInfo`: 缓存后不会频繁调用
- 构造函数注入: 避免与 `@Component` 冲突

### 2. CustomCredentialsMatcher - 密码验证

```java
@Component
public class CustomCredentialsMatcher implements CredentialsMatcher {

    @Autowired
    private UserLoginMapper userLoginMapper;

    @Override
    public boolean doCredentialsMatch(AuthenticationToken token, AuthenticationInfo info) {
        UsernamePasswordToken upToken = (UsernamePasswordToken) token;

        // 用户输入
        String inputPassword = new String(upToken.getPassword());
        String username = upToken.getUsername();

        // 数据库数据
        String storedPassword = (String) info.getCredentials();
        UserLogin userLogin = userLoginMapper.selectByUsername(username);
        String salt = userLogin.getSalt();

        // 验证密码
        return PasswordUtil.verifyPassword(inputPassword, storedPassword, salt);
    }
}
```

**关键点**:
- Shiro 会自动调用这个方法验证密码
- 需要从数据库获取盐值
- 返回 true/false 表示密码是否正确

### 3. ShiroConfig - 配置类

```java
@Configuration
public class ShiroConfig {

    /**
     * 过滤器配置
     */
    @Bean
    public ShiroFilterFactoryBean shiroFilter(SecurityManager securityManager) {
        ShiroFilterFactoryBean filterBean = new ShiroFilterFactoryBean();
        filterBean.setSecurityManager(securityManager);

        // 配置 URL 规则
        Map<String, String> filterMap = new LinkedHashMap<>();
        filterMap.put("/api/auth/login", "anon");        // 匿名访问
        filterMap.put("/api/auth/logout", "authc");      // 需要认证
        filterMap.put("/api/merchant/**", "authc,roles[MERCHANT]"); // 需要角色

        filterBean.setFilterChainDefinitionMap(filterMap);
        return filterBean;
    }

    /**
     * 安全管理器
     */
    @Bean
    public SecurityManager securityManager(UserRealm userRealm,
                                          EhCacheManager ehCacheManager) {
        DefaultWebSecurityManager securityManager = new DefaultWebSecurityManager();
        securityManager.setRealm(userRealm);
        securityManager.setCacheManager(ehCacheManager);
        return securityManager;
    }

    /**
     * 自定义 Realm
     */
    @Bean
    public UserRealm userRealm(CustomCredentialsMatcher credentialsMatcher,
                                UserLoginMapper userLoginMapper,
                                UserMapper userMapper) {
        UserRealm realm = new UserRealm(userLoginMapper, userMapper);
        realm.setCredentialsMatcher(credentialsMatcher);
        realm.setCachingEnabled(true);
        return realm;
    }

    /**
     * 注解支持
     */
    @Bean
    public AuthorizationAttributeSourceAdvisor authorizationAttributeSourceAdvisor(
            SecurityManager securityManager) {
        AuthorizationAttributeSourceAdvisor advisor = new AuthorizationAttributeSourceAdvisor();
        advisor.setSecurityManager(securityManager);
        return advisor;
    }

    /**
     * AOP 代理
     */
    @Bean
    public DefaultAdvisorAutoProxyCreator defaultAdvisorAutoProxyCreator() {
        DefaultAdvisorAutoProxyCreator proxyCreator = new DefaultAdvisorAutoProxyCreator();
        proxyCreator.setProxyTargetClass(true);
        return proxyCreator;
    }
}
```

**关键点**:
- `ShiroFilterFactoryBean`: 配置 URL 过滤规则
- `SecurityManager`: Shiro 核心，协调各个组件
- `UserRealm`: 自定义数据源
- `AuthorizationAttributeSourceAdvisor`: 支持注解
- `DefaultAdvisorAutoProxyCreator`: AOP 代理，让注解生效

### 4. PasswordUtil - 密码工具

```java
public class PasswordUtil {

    private static final int HASH_ITERATIONS = 1024;

    /**
     * 加密密码（生成新盐值）
     */
    public static String[] encryptPassword(String password) {
        String salt = UUID.randomUUID().toString().replace("-", "");
        String encryptedPassword = encryptPassword(password, salt);
        return new String[]{encryptedPassword, salt};
    }

    /**
     * 加密密码（使用指定盐值）
     */
    public static String encryptPassword(String password, String salt) {
        return new Sha256Hash(password, salt, HASH_ITERATIONS).toHex();
    }

    /**
     * 验证密码
     */
    public static boolean verifyPassword(String inputPassword,
                                         String storedPassword,
                                         String salt) {
        String encryptedInput = encryptPassword(inputPassword, salt);
        return encryptedInput.equals(storedPassword);
    }
}
```

**关键点**:
- SHA-256 算法
- 1024 次迭代
- 每个用户独立盐值
- 返回十六进制字符串

---

## 架构设计图

### 系统架构

```
┌───────────────────────────────────────────────────────────┐
│                        客户端                              │
│                 (Browser / Postman / curl)                │
└─────────────────────────┬─────────────────────────────────┘
                          │ HTTP Request
                          ▼
┌─────────────────────────────────────────────────────────┐
│                   ShiroFilter                            │
│              (拦截请求，检查认证和授权)                      │
└─────────────────────────┬───────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────┐
│                  Controller Layer                        │
│              (AuthController, UserController)            │
├──────────────────┬──────────────────────────────────────┤
│ @RequiresRoles   │ @RequiresPermissions                 │
│ 注解触发权限检查  │                                       │
└──────────────────┴──────────┬───────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────┐
│                   Service Layer                          │
│         (AuthService - 业务逻辑处理)                      │
├──────────┬─────────────┬────────────────────────────────┤
│ Subject  │ login()     │ logout()                       │
│ 操作     │ register()  │ getCurrentUser()               │
└──────────┴─────────────┴──────┬─────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────┐
│                 SecurityManager                          │
│            (Shiro 核心，协调所有组件)                      │
├─────────────┬──────────────┬─────────────┬──────────────┤
│ Authenticator│ Authorizer  │SessionMgr   │ CacheMgr     │
└─────┬───────┴──────┬───────┴──────┬──────┴──────┬───────┘
      │              │              │             │
      ▼              ▼              ▼             ▼
┌──────────┐  ┌───────────┐  ┌─────────┐  ┌──────────┐
│ UserRealm│  │ UserRealm │  │ Session │  │ EhCache  │
│ (认证)   │  │ (授权)    │  │         │  │          │
└────┬─────┘  └─────┬─────┘  └─────────┘  └──────────┘
     │              │
     ▼              ▼
┌─────────────────────────────┐
│   CustomCredentialsMatcher  │
│      (密码验证)              │
└──────────────┬──────────────┘
               │
               ▼
┌─────────────────────────────┐
│       PasswordUtil           │
│   (SHA-256 + Salt 加密)      │
└──────────────┬──────────────┘
               │
               ▼
┌─────────────────────────────────────────────────┐
│                  Mapper Layer                    │
│      (UserLoginMapper, UserMapper)              │
└──────────────────────┬──────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────┐
│                   Database                       │
│         (t_user, t_user_login)                  │
└─────────────────────────────────────────────────┘
```

### 数据流向

#### 登录流程

```
用户输入密码
    ↓
Controller 接收
    ↓
Service 创建 UsernamePasswordToken
    ↓
Subject.login(token)
    ↓
SecurityManager.authenticate()
    ↓
UserRealm.doGetAuthenticationInfo()
    ├─ 查询数据库
    ├─ 检查状态
    └─ 返回 AuthenticationInfo
    ↓
CustomCredentialsMatcher.doCredentialsMatch()
    ├─ 获取用户输入的密码
    ├─ 获取数据库密码和盐值
    ├─ PasswordUtil.verifyPassword()
    └─ 返回 true/false
    ↓
成功 → 创建 Session → 返回 Token
失败 → 抛出异常 → 返回错误信息
```

#### 权限检查流程

```
用户访问需要权限的接口
    ↓
ShiroFilter 拦截 或 @RequiresRoles 注解触发
    ↓
SecurityManager.checkPermission()
    ↓
从缓存获取 AuthorizationInfo（如果有）
    │
    └─ 缓存未命中 ↓
        UserRealm.doGetAuthorizationInfo()
            ├─ 查询用户类型
            ├─ 添加角色
            ├─ 添加权限
            └─ 返回 AuthorizationInfo
    ↓
比对用户的角色/权限
    ↓
有权限 → 继续执行业务逻辑
无权限 → 返回 403 Forbidden
```

---

## 完整执行流程

### 用户注册流程

```
POST /api/auth/register/merchant
{
  "username": "test_merchant",
  "password": "123456",
  "phone": "13900001111"
}
    ↓
AuthController.registerMerchant()
    ↓
AuthService.register(request, "MERCHANT")
    ├─ 1. 校验用户名是否存在
    ├─ 2. 创建 User 记录（t_user）
    ├─ 3. 生成密码盐值
    ├─ 4. PasswordUtil.encryptPassword("123456")
    │     ├─ 生成随机盐值
    │     └─ SHA-256 加密 × 1024次
    ├─ 5. 创建 UserLogin 记录（t_user_login）
    │     ├─ user_id
    │     ├─ username
    │     ├─ password (加密后)
    │     ├─ user_type = "MERCHANT"
    │     └─ salt
    └─ 6. 返回 userId
```

### 用户登录流程

```
POST /api/auth/login
{
  "username": "merchant1",
  "password": "123456"
}
    ↓
AuthController.login()
    ↓
AuthService.login(request, loginIp)
    ├─ 1. Subject subject = SecurityUtils.getSubject();
    ├─ 2. UsernamePasswordToken token = new UsernamePasswordToken(
    │         "merchant1", "123456"
    │     );
    ├─ 3. subject.login(token);
    │     ↓
    │     SecurityManager 处理
    │     ↓
    │     UserRealm.doGetAuthenticationInfo(token)
    │     ├─ 查询: userLoginMapper.selectByUsername("merchant1")
    │     ├─ 检查: userLogin.getStatus() == 1
    │     └─ 返回: new SimpleAuthenticationInfo(
    │             "merchant1",
    │             "30f8e16932e191d7be1b9d7a...",  // 数据库密码
    │             "userRealm"
    │         )
    │     ↓
    │     CustomCredentialsMatcher.doCredentialsMatch(token, info)
    │     ├─ inputPassword = "123456"
    │     ├─ storedPassword = "30f8e16932e191d7be1b9d7a..."
    │     ├─ salt = "salt1merchant1234567890abcdefghij"
    │     ├─ encrypted = PasswordUtil.encryptPassword("123456", salt)
    │     └─ return encrypted.equals(storedPassword) // true
    │     ↓
    │     登录成功！
    ├─ 4. 获取 Session: subject.getSession().getId()
    ├─ 5. 更新登录信息: last_login_time, last_login_ip
    └─ 6. 返回 UserLoginVO
        {
          "userId": 1,
          "username": "merchant1",
          "token": "session-id-xxx",
          "expiresIn": 1800,
          "userType": "MERCHANT"
        }
```

### 权限检查流程

```
GET /api/auth/test/merchant
Cookie: SHIROSESSIONID=xxx
    ↓
ShiroFilter 拦截
    ↓
检查 URL 规则: "/api/auth/test/merchant"
    ↓
发现需要 @RequiresRoles("MERCHANT")
    ↓
SecurityManager.checkRole("MERCHANT")
    ↓
从缓存获取 AuthorizationInfo
    │
    └─ 缓存未命中 ↓
        UserRealm.doGetAuthorizationInfo(principals)
        ├─ username = "merchant1"
        ├─ userLogin = selectByUsername("merchant1")
        ├─ userType = "MERCHANT"
        ├─ roles = ["MERCHANT"]
        ├─ permissions = [
        │     "product:create",
        │     "product:update",
        │     "product:delete",
        │     "product:view",
        │     "order:view",
        │     "order:process"
        │   ]
        └─ return new SimpleAuthorizationInfo(roles, permissions)
    ↓
检查: user.hasRole("MERCHANT") ?
    ↓
true → 执行 testMerchant() 方法
false → 返回 403 Forbidden
```

---

## 总结

### Shiro 核心原理

1. **Subject**: 用户门面，简化 API
2. **SecurityManager**: 核心管理器，协调所有组件
3. **Realm**: 数据源连接器，获取认证和授权数据
4. **CredentialsMatcher**: 密码验证器

### 本项目特色

1. **双表设计**: 用户信息表 + 用户登录表
2. **三种用户类型**: 商家、买家、管理员
3. **自定义密码匹配**: SHA-256 + Salt + 1024次迭代
4. **灵活的权限控制**: URL 过滤 + 方法注解
5. **完整的异常处理**: 统一异常捕获和响应

### 关键优化

1. **构造函数注入**: 避免循环依赖
2. **授权缓存**: 减少数据库查询
3. **Session 管理**: EhCache 提高性能
4. **版本兼容**: Spring Boot 2.7.18 兼容 Shiro

---

**文档版本**: v1.0
**创建日期**: 2026-03-10
**适用版本**: Spring Boot 2.7.18 + Apache Shiro 2.0.1
