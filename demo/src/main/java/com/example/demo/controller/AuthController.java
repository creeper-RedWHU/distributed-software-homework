package com.example.demo.controller;

import com.example.demo.common.Result;
import com.example.demo.model.dto.UserLoginRequest;
import com.example.demo.model.dto.UserRegisterRequest;
import com.example.demo.model.vo.UserLoginVO;
import com.example.demo.service.AuthService;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.apache.shiro.authz.annotation.RequiresRoles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

/**
 * 认证 Controller
 * 基于 Apache Shiro 实现的登录、注册、授权功能
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    /**
     * 用户注册（商家）
     * POST /api/auth/register/merchant
     */
    @PostMapping("/register/merchant")
    public Result<Long> registerMerchant(@Validated @RequestBody UserRegisterRequest request) {
        log.info("商家注册请求，username={}", request.getUsername());
        return authService.register(request, "MERCHANT");
    }

    /**
     * 用户注册（买家）
     * POST /api/auth/register/buyer
     */
    @PostMapping("/register/buyer")
    public Result<Long> registerBuyer(@Validated @RequestBody UserRegisterRequest request) {
        log.info("买家注册请求，username={}", request.getUsername());
        return authService.register(request, "BUYER");
    }

    /**
     * 用户注册（管理员）
     * POST /api/auth/register/admin
     * 需要管理员权限才能创建管理员账号
     */
    @PostMapping("/register/admin")
    @RequiresRoles("ADMIN")
    public Result<Long> registerAdmin(@Validated @RequestBody UserRegisterRequest request) {
        log.info("管理员注册请求，username={}", request.getUsername());
        return authService.register(request, "ADMIN");
    }

    /**
     * 用户登录
     * POST /api/auth/login
     */
    @PostMapping("/login")
    public Result<UserLoginVO> login(@Validated @RequestBody UserLoginRequest request,
                                      HttpServletRequest httpRequest) {
        String loginIp = getClientIp(httpRequest);
        log.info("用户登录请求，username={}, ip={}", request.getUsername(), loginIp);
        return authService.login(request, loginIp);
    }

    /**
     * 用户登出
     * POST /api/auth/logout
     */
    @PostMapping("/logout")
    @RequiresAuthentication
    public Result<Void> logout() {
        log.info("用户登出请求");
        return authService.logout();
    }

    /**
     * 获取当前登录用户信息
     * GET /api/auth/info
     */
    @GetMapping("/info")
    @RequiresAuthentication
    public Result<UserLoginVO> getCurrentUser() {
        log.info("获取当前用户信息");
        return authService.getCurrentUser();
    }

    /**
     * 修改密码
     * POST /api/auth/change-password
     */
    @PostMapping("/change-password")
    @RequiresAuthentication
    public Result<Void> changePassword(@RequestParam String oldPassword,
                                         @RequestParam String newPassword) {
        log.info("修改密码请求");
        return authService.changePassword(oldPassword, newPassword);
    }

    /**
     * 测试：需要商家角色
     * GET /api/auth/test/merchant
     */
    @GetMapping("/test/merchant")
    @RequiresRoles("MERCHANT")
    public Result<String> testMerchant() {
        String username = (String) SecurityUtils.getSubject().getPrincipal();
        return Result.success("商家权限测试成功，用户：" + username);
    }

    /**
     * 测试：需要买家角色
     * GET /api/auth/test/buyer
     */
    @GetMapping("/test/buyer")
    @RequiresRoles("BUYER")
    public Result<String> testBuyer() {
        String username = (String) SecurityUtils.getSubject().getPrincipal();
        return Result.success("买家权限测试成功，用户：" + username);
    }

    /**
     * 测试：需要管理员角色
     * GET /api/auth/test/admin
     */
    @GetMapping("/test/admin")
    @RequiresRoles("ADMIN")
    public Result<String> testAdmin() {
        String username = (String) SecurityUtils.getSubject().getPrincipal();
        return Result.success("管理员权限测试成功，用户：" + username);
    }

    /**
     * 测试：需要创建商品权限
     * GET /api/auth/test/create-product
     */
    @GetMapping("/test/create-product")
    @RequiresPermissions("product:create")
    public Result<String> testCreateProduct() {
        String username = (String) SecurityUtils.getSubject().getPrincipal();
        return Result.success("创建商品权限测试成功，用户：" + username);
    }

    /**
     * 测试：需要查看订单权限
     * GET /api/auth/test/view-order
     */
    @GetMapping("/test/view-order")
    @RequiresPermissions("order:view")
    public Result<String> testViewOrder() {
        String username = (String) SecurityUtils.getSubject().getPrincipal();
        return Result.success("查看订单权限测试成功，用户：" + username);
    }

    /**
     * 未授权处理
     * GET /api/auth/unauthorized
     */
    @GetMapping("/unauthorized")
    public Result<Void> unauthorized() {
        return Result.fail(403, "无权访问");
    }

    /**
     * 获取客户端 IP 地址
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 处理多个代理的情况，取第一个IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
