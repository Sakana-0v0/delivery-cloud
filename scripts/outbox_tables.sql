-- ============================================================
-- Outbox 表结构 - 迭代1
-- 执行前请确保数据库已创建:
--   - del_user_db (for user_event_outbox)
--   - del_order_db (for order_outbox)
-- ============================================================

-- -------------------------------------------------------
-- 1. user_event_outbox (在 del_user_db 中执行)
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user_event_outbox` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `event_id` VARCHAR(64) NOT NULL COMMENT '事件唯一ID（用于消费端幂等）',
    `event_type` VARCHAR(32) NOT NULL COMMENT '事件类型：REGISTER / LOGIN',
    `user_id` BIGINT NOT NULL COMMENT '关联用户ID',
    `username` VARCHAR(64) NOT NULL COMMENT '用户名',
    `email` VARCHAR(128) NOT NULL COMMENT '用户邮箱',
    `ip` VARCHAR(64) DEFAULT NULL COMMENT '登录IP（仅登录事件有值）',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT 'outbox状态：0 NEW 1 SENT 2 ACK 3 DLQ',
    `retry_count` INT NOT NULL DEFAULT 0 COMMENT '已重试次数',
    `last_error` TEXT DEFAULT NULL COMMENT '最近一次失败原因',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_event_id` (`event_id`),
    KEY `idx_status` (`status`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户事件Outbox（事务性发件箱）';

-- -------------------------------------------------------
-- 2. order_outbox (在 del_order_db 中执行)
-- -------------------------------------------------------
CREATE TABLE IF NOT EXISTS `order_outbox` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `event_id` VARCHAR(64) NOT NULL COMMENT '事件唯一ID（用于消费端幂等）',
    `event_type` VARCHAR(32) NOT NULL COMMENT '事件类型：ORDER_CREATED',
    `order_no` VARCHAR(64) NOT NULL COMMENT '关联订单号',
    `user_id` BIGINT NOT NULL COMMENT '关联用户ID',
    `username` VARCHAR(64) NOT NULL COMMENT '用户名',
    `email` VARCHAR(128) NOT NULL COMMENT '用户邮箱',
    `total_amount` DECIMAL(12,2) NOT NULL COMMENT '订单总金额',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT 'outbox状态：0 NEW 1 SENT 2 ACK 3 DLQ',
    `retry_count` INT NOT NULL DEFAULT 0 COMMENT '已重试次数',
    `last_error` TEXT DEFAULT NULL COMMENT '最近一次失败原因',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_event_id` (`event_id`),
    KEY `idx_status` (`status`),
    KEY `idx_order_no` (`order_no`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单事件Outbox（事务性发件箱）';
