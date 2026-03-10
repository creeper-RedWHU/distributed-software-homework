package com.example.demo.controller;

import com.example.demo.common.Result;
import com.example.demo.model.dto.UserLoginRequest;
import com.example.demo.model.dto.UserRegisterRequest;
import com.example.demo.model.dto.UserUpdateRequest;
import com.example.demo.model.vo.UserLoginVO;
import com.example.demo.model.vo.UserVO;
import com.example.demo.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 用户 Controller
 * 示例代码：展示完整的 REST API 设计
 */
@Slf4j
@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 用户注册
     * POST /api/user/register
     */
    @PostMapping("/register")
    public Result<Long> register(@Validated @RequestBody UserRegisterRequest request) {
        log.info("用户注册请求，username={}", request.getUsername());
        return userService.register(request);
    }

    /**
     * 用户登录
     * POST /api/user/login
     */
    @PostMapping("/login")
    public Result<UserLoginVO> login(@Validated @RequestBody UserLoginRequest request,
                                      HttpServletRequest httpRequest) {
        String loginIp = getClientIp(httpRequest);
        log.info("用户登录请求，username={}, ip={}", request.getUsername(), loginIp);
        return userService.login(request, loginIp);
    }

    /**
     * 获取用户信息
     * GET /api/user/{id}
     */
    @GetMapping("/{id}")
    public Result<UserVO> getUserById(@PathVariable Long id) {
        log.info("查询用户信息，userId={}", id);
        return userService.getUserById(id);
    }

    /**
     * 更新用户信息
     * PUT /api/user/{id}
     */
    @PutMapping("/{id}")
    public Result<Void> updateUser(@PathVariable Long id,
                                     @Validated @RequestBody UserUpdateRequest request) {
        log.info("更新用户信息，userId={}", id);
        return userService.updateUser(id, request);
    }

    /**
     * 禁用用户
     * POST /api/user/{id}/disable
     */
    @PostMapping("/{id}/disable")
    public Result<Void> disableUser(@PathVariable Long id) {
        log.info("禁用用户，userId={}", id);
        return userService.disableUser(id);
    }

    /**
     * 启用用户
     * POST /api/user/{id}/enable
     */
    @PostMapping("/{id}/enable")
    public Result<Void> enableUser(@PathVariable Long id) {
        log.info("启用用户，userId={}", id);
        return userService.enableUser(id);
    }

    /**
     * 删除用户
     * DELETE /api/user/{id}
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteUser(@PathVariable Long id) {
        log.info("删除用户，userId={}", id);
        return userService.deleteUser(id);
    }

    /**
     * 分页查询用户列表
     * GET /api/user/list?page=1&size=10
     */
    @GetMapping("/list")
    public Result<List<UserVO>> getUserList(@RequestParam(defaultValue = "1") Integer page,
                                              @RequestParam(defaultValue = "10") Integer size) {
        log.info("分页查询用户列表，page={}, size={}", page, size);
        return userService.getUserList(page, size);
    }

    /**
     * 根据条件搜索用户
     * GET /api/user/search?username=xxx&phone=xxx&status=1
     */
    @GetMapping("/search")
    public Result<List<UserVO>> searchUsers(@RequestParam(required = false) String username,
                                              @RequestParam(required = false) String phone,
                                              @RequestParam(required = false) Integer status) {
        log.info("搜索用户，username={}, phone={}, status={}", username, phone, status);
        return userService.searchUsers(username, phone, status);
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
