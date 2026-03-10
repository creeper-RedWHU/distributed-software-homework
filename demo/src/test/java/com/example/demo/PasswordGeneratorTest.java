package com.example.demo;

import com.example.demo.util.PasswordUtil;
import org.junit.jupiter.api.Test;

/**
 * 密码生成器测试
 * 用于生成测试数据的加密密码
 */
public class PasswordGeneratorTest {

    @Test
    public void generateTestPasswords() {
        System.out.println("=== 生成测试密码 ===");
        System.out.println("原始密码: 123456\n");

        // 为三个测试用户生成密码
        String[] users = {"merchant1", "buyer1", "admin1"};

        for (String username : users) {
            String[] encrypted = PasswordUtil.encryptPassword("123456");
            String password = encrypted[0];
            String salt = encrypted[1];

            System.out.println("用户: " + username);
            System.out.println("加密密码: " + password);
            System.out.println("盐值: " + salt);
            System.out.println("---");

            // SQL 插入语句
            System.out.println("UPDATE t_user_login SET password = '" + password + "', salt = '" + salt + "' WHERE username = '" + username + "';");
            System.out.println();
        }

        // 验证密码
        System.out.println("\n=== 验证密码 ===");
        String testPassword = "123456";
        String[] encrypted = PasswordUtil.encryptPassword(testPassword);
        boolean isValid = PasswordUtil.verifyPassword(testPassword, encrypted[0], encrypted[1]);
        System.out.println("密码验证结果: " + (isValid ? "成功" : "失败"));
    }

    @Test
    public void generateFixedSaltPasswords() {
        System.out.println("=== 使用固定盐值生成密码（用于 schema.sql） ===\n");

        // 使用固定盐值，方便在 SQL 中初始化
        String salt1 = "salt1merchant1234567890abcdefghij";
        String salt2 = "salt2buyer1234567890abcdefghijklm";
        String salt3 = "salt3admin1234567890abcdefghijklm";

        String password = "123456";

        System.out.println("原始密码: " + password + "\n");

        String encrypted1 = PasswordUtil.encryptPassword(password, salt1);
        System.out.println("商家 (merchant1):");
        System.out.println("  盐值: " + salt1);
        System.out.println("  加密密码: " + encrypted1);
        System.out.println("  SQL: (1, 1, 'merchant1', '" + encrypted1 + "', 'MERCHANT', '" + salt1 + "', 1)");
        System.out.println();

        String encrypted2 = PasswordUtil.encryptPassword(password, salt2);
        System.out.println("买家 (buyer1):");
        System.out.println("  盐值: " + salt2);
        System.out.println("  加密密码: " + encrypted2);
        System.out.println("  SQL: (2, 2, 'buyer1', '" + encrypted2 + "', 'BUYER', '" + salt2 + "', 1)");
        System.out.println();

        String encrypted3 = PasswordUtil.encryptPassword(password, salt3);
        System.out.println("管理员 (admin1):");
        System.out.println("  盐值: " + salt3);
        System.out.println("  加密密码: " + encrypted3);
        System.out.println("  SQL: (3, 3, 'admin1', '" + encrypted3 + "', 'ADMIN', '" + salt3 + "', 1)");
        System.out.println();

        // 生成完整的 SQL 插入语句
        System.out.println("=== 完整 SQL 插入语句 ===");
        System.out.println("INSERT IGNORE INTO t_user_login (id, user_id, username, password, user_type, salt, status) VALUES");
        System.out.println("(1, 1, 'merchant1', '" + encrypted1 + "', 'MERCHANT', '" + salt1 + "', 1),");
        System.out.println("(2, 2, 'buyer1', '" + encrypted2 + "', 'BUYER', '" + salt2 + "', 1),");
        System.out.println("(3, 3, 'admin1', '" + encrypted3 + "', 'ADMIN', '" + salt3 + "', 1);");
    }
}
