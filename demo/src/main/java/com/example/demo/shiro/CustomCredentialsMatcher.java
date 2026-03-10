package com.example.demo.shiro;

import com.example.demo.mapper.UserLoginMapper;
import com.example.demo.model.entity.UserLogin;
import com.example.demo.util.PasswordUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authc.credential.CredentialsMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 自定义密码匹配器
 * 使用 PasswordUtil 进行密码验证
 */
@Slf4j
@Component
public class CustomCredentialsMatcher implements CredentialsMatcher {

    @Autowired
    private UserLoginMapper userLoginMapper;

    @Override
    public boolean doCredentialsMatch(AuthenticationToken token, AuthenticationInfo info) {
        UsernamePasswordToken upToken = (UsernamePasswordToken) token;

        // 用户输入的密码
        String inputPassword = new String(upToken.getPassword());

        // 用户名
        String username = upToken.getUsername();

        // 从数据库查询用户登录信息获取盐值
        UserLogin userLogin = userLoginMapper.selectByUsername(username);
        if (userLogin == null) {
            log.warn("密码验证失败：用户不存在 {}", username);
            return false;
        }

        // 数据库中存储的加密密码
        String storedPassword = (String) info.getCredentials();

        // 盐值
        String salt = userLogin.getSalt();

        // 使用 PasswordUtil 验证密码
        boolean match = PasswordUtil.verifyPassword(inputPassword, storedPassword, salt);

        if (match) {
            log.info("密码验证成功：{}", username);
        } else {
            log.warn("密码验证失败：{}", username);
        }

        return match;
    }
}
