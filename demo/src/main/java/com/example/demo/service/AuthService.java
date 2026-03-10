package com.example.demo.service;

import com.example.demo.common.ErrorCode;
import com.example.demo.common.Result;
import com.example.demo.mapper.UserLoginMapper;
import com.example.demo.mapper.UserMapper;
import com.example.demo.model.dto.UserLoginRequest;
import com.example.demo.model.dto.UserRegisterRequest;
import com.example.demo.model.entity.User;
import com.example.demo.model.entity.UserLogin;
import com.example.demo.model.enums.UserTypeEnum;
import com.example.demo.model.vo.UserLoginVO;
import com.example.demo.util.PasswordUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.*;
import org.apache.shiro.subject.Subject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 认证服务
 * 基于 Apache Shiro 实现登录、注册、登出等功能
 */
@Slf4j
@Service
public class AuthService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserLoginMapper userLoginMapper;

    /**
     * 用户注册
     * @param request 注册请求
     * @param userType 用户类型（MERCHANT/BUYER/ADMIN）
     * @return 用户ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<Long> register(UserRegisterRequest request, String userType) {
        // 1. 校验用户类型
        UserTypeEnum typeEnum = UserTypeEnum.fromCode(userType);
        if (typeEnum == null) {
            return Result.fail(ErrorCode.PARAM_ERROR, "无效的用户类型");
        }

        // 2. 校验用户名是否已存在
        UserLogin existLogin = userLoginMapper.selectByUsername(request.getUsername());
        if (existLogin != null) {
            return Result.fail(ErrorCode.USER_ALREADY_EXISTS);
        }

        User existUser = userMapper.selectByUsername(request.getUsername());
        if (existUser != null) {
            return Result.fail(ErrorCode.USER_ALREADY_EXISTS);
        }

        // 3. 校验手机号是否已注册
        if (request.getPhone() != null) {
            User phoneUser = userMapper.selectByPhone(request.getPhone());
            if (phoneUser != null) {
                return Result.fail(ErrorCode.PHONE_ALREADY_EXISTS);
            }
        }

        // 4. 创建用户信息
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPasswordHash(""); // 密码存储在登录表中
        user.setPhone(request.getPhone());
        user.setEmail(request.getEmail());
        user.setStatus(1); // 默认正常状态

        int rows = userMapper.insert(user);
        if (rows == 0) {
            return Result.fail(ErrorCode.SYSTEM_ERROR, "用户信息创建失败");
        }

        // 5. 创建登录信息（使用 Shiro 密码加密）
        String[] encrypted = PasswordUtil.encryptPassword(request.getPassword());
        String encryptedPassword = encrypted[0];
        String salt = encrypted[1];

        UserLogin userLogin = new UserLogin();
        userLogin.setUserId(user.getId());
        userLogin.setUsername(request.getUsername());
        userLogin.setPassword(encryptedPassword);
        userLogin.setUserType(userType);
        userLogin.setSalt(salt);
        userLogin.setStatus(1);

        int loginRows = userLoginMapper.insert(userLogin);
        if (loginRows == 0) {
            throw new RuntimeException("登录信息创建失败");
        }

        log.info("用户注册成功，userId={}, username={}, userType={}", user.getId(), user.getUsername(), userType);
        return Result.success(user.getId());
    }

    /**
     * 用户登录（使用 Shiro）
     * @param request 登录请求
     * @param loginIp 登录IP
     * @return 登录信息
     */
    public Result<UserLoginVO> login(UserLoginRequest request, String loginIp) {
        Subject subject = SecurityUtils.getSubject();

        // 如果已经登录，先登出
        if (subject.isAuthenticated()) {
            subject.logout();
        }

        // 创建认证令牌
        UsernamePasswordToken token = new UsernamePasswordToken(
                request.getUsername(),
                request.getPassword()
        );
        token.setRememberMe(false);

        try {
            // 执行登录（会调用 UserRealm 的 doGetAuthenticationInfo 方法）
            subject.login(token);

            // 登录成功，获取用户信息
            String username = (String) subject.getPrincipal();
            UserLogin userLogin = userLoginMapper.selectByUsername(username);
            User user = userMapper.selectById(userLogin.getUserId());

            // 更新登录信息
            String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            userMapper.updateLoginInfo(user.getId(), now, loginIp);

            // 生成 Token（这里简化处理，实际可以使用 JWT）
            String sessionId = subject.getSession().getId().toString();

            // 返回登录信息
            UserLoginVO loginVO = new UserLoginVO();
            loginVO.setUserId(user.getId());
            loginVO.setUsername(user.getUsername());
            loginVO.setToken(sessionId);
            loginVO.setExpiresIn(1800L); // 30分钟
            loginVO.setUserType(userLogin.getUserType());

            log.info("用户登录成功，userId={}, username={}, userType={}", user.getId(), user.getUsername(), userLogin.getUserType());
            return Result.success(loginVO);

        } catch (UnknownAccountException e) {
            log.warn("登录失败：用户不存在，username={}", request.getUsername());
            return Result.fail(ErrorCode.USER_NOT_FOUND);
        } catch (IncorrectCredentialsException e) {
            log.warn("登录失败：密码错误，username={}", request.getUsername());
            return Result.fail(ErrorCode.PASSWORD_ERROR);
        } catch (LockedAccountException e) {
            log.warn("登录失败：账号已锁定，username={}", request.getUsername());
            return Result.fail(ErrorCode.USER_DISABLED);
        } catch (AuthenticationException e) {
            log.error("登录失败：认证异常，username={}", request.getUsername(), e);
            return Result.fail(ErrorCode.SYSTEM_ERROR, "登录失败：" + e.getMessage());
        }
    }

    /**
     * 用户登出
     */
    public Result<Void> logout() {
        try {
            Subject subject = SecurityUtils.getSubject();
            if (subject.isAuthenticated()) {
                String username = (String) subject.getPrincipal();
                subject.logout();
                log.info("用户登出成功，username={}", username);
            }
            return Result.success(null);
        } catch (Exception e) {
            log.error("登出失败", e);
            return Result.fail(ErrorCode.SYSTEM_ERROR, "登出失败");
        }
    }

    /**
     * 获取当前登录用户信息
     */
    public Result<UserLoginVO> getCurrentUser() {
        Subject subject = SecurityUtils.getSubject();
        if (!subject.isAuthenticated()) {
            return Result.fail(ErrorCode.NOT_LOGIN);
        }

        String username = (String) subject.getPrincipal();
        UserLogin userLogin = userLoginMapper.selectByUsername(username);
        if (userLogin == null) {
            return Result.fail(ErrorCode.USER_NOT_FOUND);
        }

        User user = userMapper.selectById(userLogin.getUserId());
        if (user == null) {
            return Result.fail(ErrorCode.USER_NOT_FOUND);
        }

        UserLoginVO loginVO = new UserLoginVO();
        loginVO.setUserId(user.getId());
        loginVO.setUsername(user.getUsername());
        loginVO.setUserType(userLogin.getUserType());

        return Result.success(loginVO);
    }

    /**
     * 修改密码
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> changePassword(String oldPassword, String newPassword) {
        Subject subject = SecurityUtils.getSubject();
        if (!subject.isAuthenticated()) {
            return Result.fail(ErrorCode.NOT_LOGIN);
        }

        String username = (String) subject.getPrincipal();
        UserLogin userLogin = userLoginMapper.selectByUsername(username);
        if (userLogin == null) {
            return Result.fail(ErrorCode.USER_NOT_FOUND);
        }

        // 验证旧密码
        if (!PasswordUtil.verifyPassword(oldPassword, userLogin.getPassword(), userLogin.getSalt())) {
            return Result.fail(ErrorCode.PASSWORD_ERROR, "原密码错误");
        }

        // 加密新密码
        String[] encrypted = PasswordUtil.encryptPassword(newPassword);
        String encryptedPassword = encrypted[0];
        String newSalt = encrypted[1];

        // 更新密码
        int rows = userLoginMapper.updatePassword(userLogin.getId(), encryptedPassword, newSalt);
        if (rows > 0) {
            log.info("用户密码修改成功，username={}", username);
            // 密码修改后，强制登出
            subject.logout();
            return Result.success(null);
        }

        return Result.fail(ErrorCode.SYSTEM_ERROR, "密码修改失败");
    }
}
