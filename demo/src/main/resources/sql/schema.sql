-- =============================================
-- 商品库存与秒杀系统 - 数据库初始化脚本
-- =============================================

CREATE DATABASE IF NOT EXISTS seckill_db DEFAULT CHARACTER SET utf8mb4;
USE seckill_db;

-- 商品分类表
CREATE TABLE IF NOT EXISTS t_category (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL COMMENT '分类名称',
    parent_id   BIGINT DEFAULT 0 COMMENT '父分类ID，0为顶级分类',
    sort_order  INT DEFAULT 0 COMMENT '排序权重',
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_parent(parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品分类表';

-- 商品表
CREATE TABLE IF NOT EXISTS t_product (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    name         VARCHAR(200) NOT NULL COMMENT '商品名称',
    description  TEXT COMMENT '商品描述',
    price        DECIMAL(10,2) NOT NULL COMMENT '原价',
    image_url    VARCHAR(500) COMMENT '商品图片URL',
    category_id  BIGINT NOT NULL COMMENT '分类ID',
    status       TINYINT DEFAULT 1 COMMENT '状态: 0下架 1上架',
    created_at   DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_category(category_id),
    INDEX idx_status(status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表';

-- 秒杀活动表（核心）
CREATE TABLE IF NOT EXISTS t_seckill_activity (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(200) NOT NULL COMMENT '活动名称',
    product_id      BIGINT NOT NULL COMMENT '关联商品ID',
    seckill_price   DECIMAL(10,2) NOT NULL COMMENT '秒杀价格',
    total_stock     INT NOT NULL COMMENT '总库存',
    available_stock INT NOT NULL COMMENT '可用库存（乐观锁扣减）',
    start_time      DATETIME NOT NULL COMMENT '秒杀开始时间',
    end_time        DATETIME NOT NULL COMMENT '秒杀结束时间',
    status          TINYINT DEFAULT 0 COMMENT '状态: 0未开始 1进行中 2已结束 3已取消',
    limit_per_user  INT DEFAULT 1 COMMENT '每人限购数量',
    version         INT DEFAULT 0 COMMENT '乐观锁版本号',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_product(product_id),
    INDEX idx_status_time(status, start_time, end_time),
    INDEX idx_start_time(start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀活动表';

-- 订单表
CREATE TABLE IF NOT EXISTS t_order (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no      VARCHAR(64) NOT NULL UNIQUE COMMENT '订单号',
    user_id       BIGINT NOT NULL COMMENT '用户ID',
    activity_id   BIGINT NOT NULL COMMENT '秒杀活动ID',
    product_id    BIGINT NOT NULL COMMENT '商品ID',
    quantity      INT NOT NULL DEFAULT 1 COMMENT '购买数量',
    total_amount  DECIMAL(10,2) NOT NULL COMMENT '订单总金额',
    status        TINYINT DEFAULT 0 COMMENT '状态: 0待支付 1已支付 2已取消 3已退款 4已超时',
    pay_time      DATETIME COMMENT '支付时间',
    expire_time   DATETIME NOT NULL COMMENT '订单过期时间',
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX uk_user_activity(user_id, activity_id) COMMENT '防止同一用户同一活动重复下单',
    INDEX idx_user(user_id),
    INDEX idx_activity(activity_id),
    INDEX idx_status(status),
    INDEX idx_expire_time(expire_time, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀订单表';

-- 用户表
CREATE TABLE IF NOT EXISTS t_user (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    username       VARCHAR(100) NOT NULL UNIQUE COMMENT '用户名',
    password_hash  VARCHAR(256) NOT NULL COMMENT '密码哈希',
    phone          VARCHAR(20) COMMENT '手机号',
    email          VARCHAR(200) COMMENT '邮箱',
    status         TINYINT DEFAULT 1 COMMENT '状态: 0禁用 1正常',
    created_at     DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_phone(phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 库存流水表
CREATE TABLE IF NOT EXISTS t_stock_log (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    activity_id   BIGINT NOT NULL COMMENT '秒杀活动ID',
    order_no      VARCHAR(64) COMMENT '关联订单号',
    user_id       BIGINT COMMENT '用户ID',
    quantity      INT NOT NULL COMMENT '变化数量（正数补货，负数扣减）',
    type          TINYINT NOT NULL COMMENT '类型: 1扣减 2回滚 3手动补货',
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_activity(activity_id),
    INDEX idx_order(order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存流水表';

-- 事件日志表
CREATE TABLE IF NOT EXISTS t_event_log (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_type    VARCHAR(100) NOT NULL COMMENT '事件类型',
    event_data    JSON COMMENT '事件数据(JSON)',
    source        VARCHAR(100) COMMENT '事件来源',
    status        TINYINT DEFAULT 0 COMMENT '状态: 0待处理 1已处理 2处理失败',
    retry_count   INT DEFAULT 0 COMMENT '重试次数',
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    processed_at  DATETIME COMMENT '处理时间',
    INDEX idx_type_status(event_type, status),
    INDEX idx_created(created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事件日志表';

-- =============================================
-- 测试数据
-- =============================================

-- 分类
INSERT IGNORE INTO t_category (id, name) VALUES (1, '手机'), (2, '电脑'), (3, '配件');

-- 商品
INSERT IGNORE INTO t_product (id, name, description, price, image_url, category_id) VALUES
(1, 'iPhone 16 Pro', 'Apple iPhone 16 Pro 256GB', 8999.00, '/images/iphone16.jpg', 1),
(2, 'MacBook Air M4', 'Apple MacBook Air 15" M4', 9999.00, '/images/macbook.jpg', 2),
(3, 'AirPods Pro 3', 'Apple AirPods Pro 第3代', 1999.00, '/images/airpods.jpg', 3);

-- 秒杀活动
INSERT IGNORE INTO t_seckill_activity (id, name, product_id, seckill_price, total_stock, available_stock, start_time, end_time, status, limit_per_user) VALUES
(1, 'iPhone 限时秒杀', 1, 6999.00, 100, 100, '2026-03-05 10:00:00', '2026-03-05 10:30:00', 0, 1),
(2, 'MacBook 限时秒杀', 2, 7999.00, 50, 50, '2026-03-06 14:00:00', '2026-03-06 14:30:00', 0, 1);

-- 测试用户
INSERT IGNORE INTO t_user (id, username, password_hash, phone) VALUES
(1, 'testuser1', '$2a$10$placeholder', '13800138001'),
(2, 'testuser2', '$2a$10$placeholder', '13800138002');
