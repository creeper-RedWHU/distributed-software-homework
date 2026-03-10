package com.example.demo.util;

import org.apache.shiro.crypto.hash.Sha256Hash;

import java.util.UUID;

/**
 * 密码加密工具类
 */
public class PasswordUtil {

    /**
     * 加密迭代次数
     */
    private static final int HASH_ITERATIONS = 1024;

    /**
     * 生成随机盐值
     */
    public static String generateSalt() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 加密密码（使用随机盐值）
     * @param password 原始密码
     * @return 数组 [加密后的密码, 盐值]
     */
    public static String[] encryptPassword(String password) {
        String salt = generateSalt();
        String encryptedPassword = encryptPassword(password, salt);
        return new String[]{encryptedPassword, salt};
    }

    /**
     * 加密密码（使用指定盐值）
     * @param password 原始密码
     * @param salt 盐值
     * @return 加密后的密码
     */
    public static String encryptPassword(String password, String salt) {
        return new Sha256Hash(password, salt, HASH_ITERATIONS).toHex();
    }

    /**
     * 验证密码
     * @param inputPassword 输入的密码
     * @param storedPassword 存储的加密密码
     * @param salt 盐值
     * @return 是否匹配
     */
    public static boolean verifyPassword(String inputPassword, String storedPassword, String salt) {
        String encryptedInput = encryptPassword(inputPassword, salt);
        return encryptedInput.equals(storedPassword);
    }
}
