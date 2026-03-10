-- =============================================
-- 用户模块 - 数据库初始化脚本
-- =============================================

-- 用户表
CREATE TABLE IF NOT EXISTS t_user (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
    username       VARCHAR(100) NOT NULL UNIQUE COMMENT '用户名',
    password_hash  VARCHAR(256) NOT NULL COMMENT '密码哈希（BCrypt）',
    nickname       VARCHAR(100) COMMENT '昵称',
    phone          VARCHAR(20) COMMENT '手机号',
    email          VARCHAR(200) COMMENT '邮箱',
    avatar_url     VARCHAR(500) COMMENT '头像URL',
    gender         TINYINT DEFAULT 0 COMMENT '性别: 0未知 1男 2女',
    birthday       DATE COMMENT '生日',
    status         TINYINT DEFAULT 1 COMMENT '状态: 0禁用 1正常 2锁定',
    last_login_time DATETIME COMMENT '最后登录时间',
    last_login_ip  VARCHAR(50) COMMENT '最后登录IP',
    created_at     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE INDEX uk_username(username),
    INDEX idx_phone(phone),
    INDEX idx_email(email),
    INDEX idx_status(status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 用户登录表
CREATE TABLE IF NOT EXISTS t_user_login (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    user_id     BIGINT NOT NULL COMMENT '用户ID',
    username    VARCHAR(100) NOT NULL UNIQUE COMMENT '登录用户名',
    password    VARCHAR(256) NOT NULL COMMENT '登录密码（BCrypt加密）',
    user_type   VARCHAR(20) NOT NULL COMMENT '用户类型: MERCHANT(商家)/BUYER(买家)/ADMIN(管理者)',
    salt        VARCHAR(64) COMMENT '密码盐值',
    status      TINYINT DEFAULT 1 COMMENT '状态: 0禁用 1正常',
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE INDEX uk_username(username),
    INDEX idx_user_id(user_id),
    INDEX idx_user_type(user_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户登录表';

-- 用户角色表
CREATE TABLE IF NOT EXISTS t_user_role (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    user_id    BIGINT NOT NULL COMMENT '用户ID',
    role_code  VARCHAR(50) NOT NULL COMMENT '角色代码: USER/ADMIN/VIP',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE INDEX uk_user_role(user_id, role_code),
    INDEX idx_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色表';

-- 用户地址表
CREATE TABLE IF NOT EXISTS t_user_address (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '地址ID',
    user_id       BIGINT NOT NULL COMMENT '用户ID',
    receiver_name VARCHAR(100) NOT NULL COMMENT '收货人姓名',
    receiver_phone VARCHAR(20) NOT NULL COMMENT '收货人电话',
    province      VARCHAR(50) NOT NULL COMMENT '省份',
    city          VARCHAR(50) NOT NULL COMMENT '城市',
    district      VARCHAR(50) NOT NULL COMMENT '区县',
    detail_address VARCHAR(500) NOT NULL COMMENT '详细地址',
    is_default    TINYINT DEFAULT 0 COMMENT '是否默认地址: 0否 1是',
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户地址表';

-- =============================================
-- 测试数据（密码: 123456, 使用 SHA-256 + 盐值加密）
-- =============================================
INSERT IGNORE INTO t_user (id, username, password_hash, nickname, phone, status) VALUES
(1, 'merchant1', '', '测试商家', '13800138001', 1),
(2, 'buyer1', '', '测试买家', '13800138002', 1),
(3, 'admin1', '', '系统管理员', '13800138003', 1);

-- 用户登录表测试数据（真实加密密码）
INSERT IGNORE INTO t_user_login (id, user_id, username, password, user_type, salt, status) VALUES
(1, 1, 'merchant1', '30f8e16932e191d7be1b9d7a163dd8596b1ce7706090143e559e282eff2d3f2c', 'MERCHANT', 'salt1merchant1234567890abcdefghij', 1),
(2, 2, 'buyer1', 'f73a040aa367ab1170cd8aeec57009a2a982f8f85ac983f3db56f5dcb4d7723a', 'BUYER', 'salt2buyer1234567890abcdefghijklm', 1),
(3, 3, 'admin1', '7f09e1c6d7d290eb8e1bdaac690beb085798898ea081e5e3c4dc5aff42060fb5', 'ADMIN', 'salt3admin1234567890abcdefghijklm', 1);
