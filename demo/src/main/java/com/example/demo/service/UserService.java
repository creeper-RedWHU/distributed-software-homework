package com.example.demo.service;

import com.example.demo.common.ErrorCode;
import com.example.demo.common.Result;
import com.example.demo.mapper.UserMapper;
import com.example.demo.model.dto.UserLoginRequest;
import com.example.demo.model.dto.UserRegisterRequest;
import com.example.demo.model.dto.UserUpdateRequest;
import com.example.demo.model.entity.User;
import com.example.demo.model.vo.UserLoginVO;
import com.example.demo.model.vo.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户服务实现类
 * 示例代码：展示完整的 CRUD 操作
 */
@Slf4j
@Service
public class UserService {

    @Autowired
    private UserMapper userMapper;

    /**
     * 用户注册
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<Long> register(UserRegisterRequest request) {
        // 1. 校验用户名是否已存在
        User existUser = userMapper.selectByUsername(request.getUsername());
        if (existUser != null) {
            return Result.fail(ErrorCode.USER_ALREADY_EXISTS);
        }

        // 2. 校验手机号是否已注册
        if (request.getPhone() != null) {
            User phoneUser = userMapper.selectByPhone(request.getPhone());
            if (phoneUser != null) {
                return Result.fail(ErrorCode.PHONE_ALREADY_EXISTS);
            }
        }

        // 3. 创建用户对象
        User user = new User();
        user.setUsername(request.getUsername());
        // TODO: 使用 BCrypt 加密密码
        user.setPasswordHash(encryptPassword(request.getPassword()));
        user.setPhone(request.getPhone());
        user.setEmail(request.getEmail());
        user.setStatus(1); // 默认正常状态

        // 4. 插入数据库
        int rows = userMapper.insert(user);
        if (rows > 0) {
            log.info("用户注册成功，userId={}, username={}", user.getId(), user.getUsername());
            return Result.success(user.getId());
        }

        return Result.fail(ErrorCode.SYSTEM_ERROR);
    }

    /**
     * 用户登录
     */
    public Result<UserLoginVO> login(UserLoginRequest request, String loginIp) {
        // 1. 查询用户
        User user = userMapper.selectByUsername(request.getUsername());
        if (user == null) {
            return Result.fail(ErrorCode.USER_NOT_FOUND);
        }

        // 2. 验证密码
        if (!verifyPassword(request.getPassword(), user.getPasswordHash())) {
            return Result.fail(ErrorCode.PASSWORD_ERROR);
        }

        // 3. 检查用户状态
        if (user.getStatus() != 1) {
            return Result.fail(ErrorCode.USER_DISABLED);
        }

        // 4. 更新登录信息
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        userMapper.updateLoginInfo(user.getId(), now, loginIp);

        // 5. 生成 Token（这里简化处理）
        String token = generateToken(user);

        // 6. 返回登录信息
        UserLoginVO loginVO = new UserLoginVO();
        loginVO.setUserId(user.getId());
        loginVO.setUsername(user.getUsername());
        loginVO.setToken(token);
        loginVO.setExpiresIn(7200L); // 2小时

        log.info("用户登录成功，userId={}, username={}", user.getId(), user.getUsername());
        return Result.success(loginVO);
    }

    /**
     * 根据ID获取用户信息
     */
    public Result<UserVO> getUserById(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.fail(ErrorCode.USER_NOT_FOUND);
        }

        UserVO userVO = convertToVO(user);
        return Result.success(userVO);
    }

    /**
     * 更新用户信息
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> updateUser(Long userId, UserUpdateRequest request) {
        // 1. 查询用户是否存在
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.fail(ErrorCode.USER_NOT_FOUND);
        }

        // 2. 更新字段
        User updateUser = new User();
        updateUser.setId(userId);
        BeanUtils.copyProperties(request, updateUser);

        // 3. 执行更新
        int rows = userMapper.update(updateUser);
        if (rows > 0) {
            log.info("用户信息更新成功，userId={}", userId);
            return Result.success(null);
        }

        return Result.fail(ErrorCode.SYSTEM_ERROR);
    }

    /**
     * 禁用用户
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> disableUser(Long userId) {
        int rows = userMapper.updateStatus(userId, 0);
        if (rows > 0) {
            log.info("用户已禁用，userId={}", userId);
            return Result.success(null);
        }
        return Result.fail(ErrorCode.USER_NOT_FOUND);
    }

    /**
     * 启用用户
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> enableUser(Long userId) {
        int rows = userMapper.updateStatus(userId, 1);
        if (rows > 0) {
            log.info("用户已启用，userId={}", userId);
            return Result.success(null);
        }
        return Result.fail(ErrorCode.USER_NOT_FOUND);
    }

    /**
     * 删除用户（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> deleteUser(Long userId) {
        int rows = userMapper.deleteById(userId);
        if (rows > 0) {
            log.info("用户已删除，userId={}", userId);
            return Result.success(null);
        }
        return Result.fail(ErrorCode.USER_NOT_FOUND);
    }

    /**
     * 分页查询用户列表
     */
    public Result<List<UserVO>> getUserList(Integer page, Integer size) {
        int offset = (page - 1) * size;
        List<User> users = userMapper.selectList(offset, size);

        List<UserVO> userVOList = users.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        return Result.success(userVOList);
    }

    /**
     * 根据条件查询用户
     */
    public Result<List<UserVO>> searchUsers(String username, String phone, Integer status) {
        List<User> users = userMapper.selectByCondition(username, phone, status);

        List<UserVO> userVOList = users.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        return Result.success(userVOList);
    }

    /**
     * 转换为 VO
     */
    private UserVO convertToVO(User user) {
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }

    /**
     * 密码加密（简化处理，实际应使用 BCrypt）
     */
    private String encryptPassword(String password) {
        // TODO: 使用 BCryptPasswordEncoder 加密
        // return new BCryptPasswordEncoder().encode(password);
        return "hashed_" + password; // 示例代码，实际使用加密
    }

    /**
     * 验证密码（简化处理）
     */
    private boolean verifyPassword(String rawPassword, String encodedPassword) {
        // TODO: 使用 BCryptPasswordEncoder 验证
        // return new BCryptPasswordEncoder().matches(rawPassword, encodedPassword);
        return encodedPassword.equals("hashed_" + rawPassword); // 示例代码
    }

    /**
     * 生成 Token（简化处理，实际应使用 JWT）
     */
    private String generateToken(User user) {
        // TODO: 使用 JWT 生成 Token
        // return Jwts.builder()
        //     .setSubject(user.getId().toString())
        //     .setIssuedAt(new Date())
        //     .setExpiration(new Date(System.currentTimeMillis() + 7200000))
        //     .signWith(SignatureAlgorithm.HS512, SECRET_KEY)
        //     .compact();
        return "token_" + user.getId() + "_" + System.currentTimeMillis(); // 示例代码
    }
}
