package com.example.demo.shiro;

import com.example.demo.mapper.UserLoginMapper;
import com.example.demo.mapper.UserMapper;
import com.example.demo.model.entity.User;
import com.example.demo.model.entity.UserLogin;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authc.*;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.SimplePrincipalCollection;

import java.util.HashSet;
import java.util.Set;

/**
 * 自定义 Shiro Realm
 * 负责认证（登录）和授权（权限）
 */
@Slf4j
public class UserRealm extends AuthorizingRealm {

    private final UserLoginMapper userLoginMapper;
    private final UserMapper userMapper;

    /**
     * 构造函数注入（由 ShiroConfig 的 @Bean 方法调用）
     */
    public UserRealm(UserLoginMapper userLoginMapper, UserMapper userMapper) {
        this.userLoginMapper = userLoginMapper;
        this.userMapper = userMapper;
    }

    /**
     * 授权：获取用户的角色和权限信息
     */
    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        log.info("执行授权逻辑");

        // 获取当前登录用户的用户名
        String username = (String) principals.getPrimaryPrincipal();

        // 查询用户登录信息
        UserLogin userLogin = userLoginMapper.selectByUsername(username);
        if (userLogin == null) {
            return null;
        }

        SimpleAuthorizationInfo info = new SimpleAuthorizationInfo();

        // 添加角色（根据用户类型）
        Set<String> roles = new HashSet<>();
        roles.add(userLogin.getUserType()); // MERCHANT, BUYER, ADMIN
        info.setRoles(roles);

        // 添加权限（根据用户类型分配不同权限）
        Set<String> permissions = new HashSet<>();
        switch (userLogin.getUserType()) {
            case "ADMIN":
                // 管理员拥有所有权限
                permissions.add("user:*");
                permissions.add("product:*");
                permissions.add("order:*");
                permissions.add("system:*");
                break;
            case "MERCHANT":
                // 商家权限
                permissions.add("product:create");
                permissions.add("product:update");
                permissions.add("product:delete");
                permissions.add("product:view");
                permissions.add("order:view");
                permissions.add("order:process");
                break;
            case "BUYER":
                // 买家权限
                permissions.add("product:view");
                permissions.add("order:create");
                permissions.add("order:view");
                permissions.add("order:cancel");
                break;
            default:
                break;
        }
        info.setStringPermissions(permissions);

        log.info("用户 {} 的角色: {}, 权限: {}", username, roles, permissions);
        return info;
    }

    /**
     * 认证：验证用户登录
     */
    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) throws AuthenticationException {
        log.info("执行认证逻辑");

        UsernamePasswordToken upToken = (UsernamePasswordToken) token;
        String username = upToken.getUsername();

        // 1. 查询用户登录信息
        UserLogin userLogin = userLoginMapper.selectByUsername(username);
        if (userLogin == null) {
            log.warn("用户不存在: {}", username);
            throw new UnknownAccountException("用户不存在");
        }

        // 2. 检查账号状态
        if (userLogin.getStatus() == 0) {
            log.warn("账号已被禁用: {}", username);
            throw new LockedAccountException("账号已被禁用");
        }

        // 3. 检查用户信息表状态
        User user = userMapper.selectById(userLogin.getUserId());
        if (user == null || user.getStatus() != 1) {
            log.warn("用户状态异常: {}", username);
            throw new LockedAccountException("账号状态异常");
        }

        // 4. 返回认证信息
        // Shiro会自动进行密码比对
        // 参数说明：
        // - principal: 身份信息，通常是用户名
        // - credentials: 凭证信息，从数据库查询的加密密码
        // - realmName: Realm名称

        // 注意：盐值已经在密码加密时使用，存储的密码已包含盐值效果
        // 因此这里直接返回用户名和密码即可
        SimpleAuthenticationInfo authenticationInfo = new SimpleAuthenticationInfo(
                username,                    // principal: 身份信息
                userLogin.getPassword(),     // credentials: 密码
                getName()                    // realmName: Realm名称
        );

        log.info("用户 {} 认证成功，用户类型: {}", username, userLogin.getUserType());
        return authenticationInfo;
    }

    /**
     * 清除指定用户的授权缓存
     */
    public void clearAuthorizationCache(String username) {
        SimplePrincipalCollection principals = new SimplePrincipalCollection(username, getName());
        super.clearCachedAuthorizationInfo(principals);
    }
}
