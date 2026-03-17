package com.example.demo.config;

import com.example.demo.mapper.UserLoginMapper;
import com.example.demo.mapper.UserMapper;
import com.example.demo.shiro.CustomCredentialsMatcher;
import com.example.demo.shiro.UserRealm;
import org.apache.shiro.cache.ehcache.EhCacheManager;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.spring.security.interceptor.AuthorizationAttributeSourceAdvisor;
import org.apache.shiro.spring.web.ShiroFilterFactoryBean;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.springframework.aop.framework.autoproxy.DefaultAdvisorAutoProxyCreator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shiro 配置类
 */
@Configuration
public class ShiroConfig {

    /**
     * 创建 ShiroFilterFactoryBean
     * 配置拦截规则
     */
    @Bean
    public ShiroFilterFactoryBean shiroFilter(SecurityManager securityManager) {
        ShiroFilterFactoryBean filterBean = new ShiroFilterFactoryBean();
        filterBean.setSecurityManager(securityManager);

        // 设置登录、未授权跳转页面
        filterBean.setLoginUrl("/api/auth/login");
        filterBean.setUnauthorizedUrl("/api/auth/unauthorized");

        // 配置拦截规则
        Map<String, String> filterChainDefinitionMap = new LinkedHashMap<>();

        // 公开接口，无需认证
        filterChainDefinitionMap.put("/api/auth/register/**", "anon");
        filterChainDefinitionMap.put("/api/auth/login", "anon");
        filterChainDefinitionMap.put("/api/user/register", "anon");
        filterChainDefinitionMap.put("/api/user/login", "anon");
        filterChainDefinitionMap.put("/ping", "anon");
        filterChainDefinitionMap.put("/ping/", "anon");

        // 商品与秒杀 - 查询接口公开
        filterChainDefinitionMap.put("/api/product/**", "anon");
        filterChainDefinitionMap.put("/api/seckill/**", "anon");

        // 搜索与读写分离测试 - 公开
        filterChainDefinitionMap.put("/api/search/**", "anon");
        filterChainDefinitionMap.put("/api/rw-test/**", "anon");

        // 需要认证的接口
        filterChainDefinitionMap.put("/api/auth/logout", "authc");
        filterChainDefinitionMap.put("/api/auth/info", "authc");
        filterChainDefinitionMap.put("/api/auth/change-password", "authc");
        filterChainDefinitionMap.put("/api/user/logout", "authc");
        filterChainDefinitionMap.put("/api/user/info", "authc");
        filterChainDefinitionMap.put("/api/user/update", "authc");

        // 需要特定角色的接口
        // 商家权限
        filterChainDefinitionMap.put("/api/merchant/**", "authc,roles[MERCHANT]");

        // 买家权限
        filterChainDefinitionMap.put("/api/buyer/**", "authc,roles[BUYER]");

        // 管理员权限
        filterChainDefinitionMap.put("/api/admin/**", "authc,roles[ADMIN]");

        // 需要特定权限的接口
        filterChainDefinitionMap.put("/api/product/create", "authc,perms[product:create]");
        filterChainDefinitionMap.put("/api/product/update", "authc,perms[product:update]");
        filterChainDefinitionMap.put("/api/product/delete", "authc,perms[product:delete]");

        // 权限测试接口 - 使用角色和权限控制（通过注解控制，这里只需要认证即可）
        filterChainDefinitionMap.put("/api/auth/test/**", "authc");

        // 其他接口默认不拦截，由注解控制
        // filterChainDefinitionMap.put("/api/**", "authc");

        filterBean.setFilterChainDefinitionMap(filterChainDefinitionMap);
        return filterBean;
    }

    /**
     * 创建 SecurityManager
     */
    @Bean
    public SecurityManager securityManager(UserRealm userRealm, EhCacheManager ehCacheManager) {
        DefaultWebSecurityManager securityManager = new DefaultWebSecurityManager();
        securityManager.setRealm(userRealm);
        securityManager.setCacheManager(ehCacheManager);
        return securityManager;
    }

    /**
     * 配置自定义 Realm
     * 注入自定义密码匹配器和 Mapper
     */
    @Bean
    public UserRealm userRealm(CustomCredentialsMatcher credentialsMatcher,
                                UserLoginMapper userLoginMapper,
                                UserMapper userMapper) {
        UserRealm realm = new UserRealm(userLoginMapper, userMapper);
        realm.setCredentialsMatcher(credentialsMatcher);
        // 启用缓存
        realm.setCachingEnabled(true);
        realm.setAuthenticationCachingEnabled(true);
        realm.setAuthorizationCachingEnabled(true);
        return realm;
    }

    /**
     * 配置 EhCache 缓存管理器
     */
    @Bean
    public EhCacheManager ehCacheManager() {
        EhCacheManager cacheManager = new EhCacheManager();
        cacheManager.setCacheManagerConfigFile("classpath:ehcache-shiro.xml");
        return cacheManager;
    }

    /**
     * 启用 Shiro 注解支持
     * 允许使用 @RequiresRoles、@RequiresPermissions 等注解
     */
    @Bean
    public AuthorizationAttributeSourceAdvisor authorizationAttributeSourceAdvisor(SecurityManager securityManager) {
        AuthorizationAttributeSourceAdvisor advisor = new AuthorizationAttributeSourceAdvisor();
        advisor.setSecurityManager(securityManager);
        return advisor;
    }

    /**
     * 启用 AOP 代理
     */
    @Bean
    public DefaultAdvisorAutoProxyCreator defaultAdvisorAutoProxyCreator() {
        DefaultAdvisorAutoProxyCreator proxyCreator = new DefaultAdvisorAutoProxyCreator();
        proxyCreator.setProxyTargetClass(true);
        return proxyCreator;
    }
}
