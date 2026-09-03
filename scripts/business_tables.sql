-- ============================================================
-- 微服务业务表结构 - 迭代2&3
-- 执行前请确保数据库已创建
-- ============================================================

-- -------------------------------------------------------
-- 1. del_user_db 表结构
-- -------------------------------------------------------

-- 用户表
CREATE TABLE IF NOT EXISTS `t_user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username` VARCHAR(64) NOT NULL COMMENT '用户名（唯一）',
    `password` VARCHAR(128) NOT NULL COMMENT '密码（BCrypt 加密）',
    `nickname` VARCHAR(64) DEFAULT NULL COMMENT '昵称',
    `phone` VARCHAR(32) DEFAULT NULL COMMENT '手机号',
    `email` VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    `avatar` VARCHAR(512) DEFAULT NULL COMMENT '头像 URL',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态（0=正常，1=冻结）',
    `last_login_time` DATETIME DEFAULT NULL COMMENT '最近登录时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记（0=未删除，1=已删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    KEY `idx_phone` (`phone`),
    KEY `idx_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 用户地址表
CREATE TABLE IF NOT EXISTS `t_user_address` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `consignee` VARCHAR(64) NOT NULL COMMENT '收货人姓名',
    `phone` VARCHAR(32) NOT NULL COMMENT '联系电话',
    `province` VARCHAR(32) NOT NULL COMMENT '省份',
    `city` VARCHAR(32) NOT NULL COMMENT '城市',
    `district` VARCHAR(32) NOT NULL COMMENT '区县',
    `detail_address` VARCHAR(256) NOT NULL COMMENT '详细地址',
    `tag` VARCHAR(32) DEFAULT NULL COMMENT '地址标签（如：公司、家）',
    `is_default` TINYINT NOT NULL DEFAULT 0 COMMENT '是否默认地址（0=否，1=是）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户地址表';

-- -------------------------------------------------------
-- 2. del_admin_db 表结构
-- -------------------------------------------------------

-- 管理员表
CREATE TABLE IF NOT EXISTS `t_admin` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username` VARCHAR(64) NOT NULL COMMENT '用户名',
    `password` VARCHAR(128) NOT NULL COMMENT '密码（BCrypt 加密）',
    `nickname` VARCHAR(64) DEFAULT NULL COMMENT '昵称',
    `role` VARCHAR(32) NOT NULL DEFAULT 'admin' COMMENT '角色（admin/super_admin）',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态（0=正常，1=禁用）',
    `last_login_time` DATETIME DEFAULT NULL COMMENT '最近登录时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理员表';

-- 初始管理员数据（密码: admin123）
INSERT INTO `t_admin` (`username`, `password`, `nickname`, `role`, `status`) VALUES
('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '超级管理员', 'super_admin', 0);

-- -------------------------------------------------------
-- 3. del_product_db 表结构
-- -------------------------------------------------------

-- 商品分类表
CREATE TABLE IF NOT EXISTS `t_category` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name` VARCHAR(64) NOT NULL COMMENT '分类名称',
    `sort` INT NOT NULL DEFAULT 0 COMMENT '排序字段',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态（0=启用，1=禁用）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    KEY `idx_sort` (`sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品分类表';

-- 商品表
CREATE TABLE IF NOT EXISTS `t_product` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `fid` VARCHAR(64) NOT NULL COMMENT '商品FID',
    `category_id` BIGINT NOT NULL COMMENT '分类ID',
    `name` VARCHAR(128) NOT NULL COMMENT '商品名称',
    `cover` VARCHAR(512) DEFAULT NULL COMMENT '封面图片URL',
    `description` TEXT COMMENT '商品描述',
    `norm_price` DECIMAL(12,2) NOT NULL COMMENT '原价',
    `real_price` DECIMAL(12,2) NOT NULL COMMENT '现价（实际售价）',
    `stock` INT NOT NULL DEFAULT 0 COMMENT '库存',
    `sales` INT NOT NULL DEFAULT 0 COMMENT '销量',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态（0=上架，1=下架）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_fid` (`fid`),
    KEY `idx_category_id` (`category_id`),
    KEY `idx_status` (`status`),
    KEY `idx_sales` (`sales`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品表';

-- 商品评价表
CREATE TABLE IF NOT EXISTS `t_review` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` BIGINT NOT NULL COMMENT '评价用户ID',
    `order_id` BIGINT NOT NULL COMMENT '所属订单ID',
    `product_id` BIGINT NOT NULL COMMENT '被评商品ID',
    `type` TINYINT NOT NULL COMMENT '评价类型（1=赞，2=踩）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_product` (`order_id`, `product_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品评价表';

-- -------------------------------------------------------
-- 4. 初始化测试分类数据
-- -------------------------------------------------------
INSERT INTO `t_category` (`name`, `sort`, `status`) VALUES
('快餐便当', 1, 0),
('水果生鲜', 2, 0),
('甜品饮品', 3, 0),
('外卖杂货', 4, 0);
