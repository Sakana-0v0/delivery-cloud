-- ============================================================
-- 微服务迭代 评价事件日志表
-- 用途：管理员面板数据来源、Redis 计数丢失后重建、审计追溯
-- 特性：append-only（只插入，不更新不删除）
-- 执行位置：del_product_db
-- 执行时间：2026-09-04
-- ============================================================

CREATE TABLE IF NOT EXISTS `t_review_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` BIGINT NOT NULL COMMENT '评价用户ID',
    `order_id` BIGINT NOT NULL COMMENT '所属订单ID',
    `product_id` BIGINT NOT NULL COMMENT '被评价商品ID',
    `action` TINYINT NOT NULL COMMENT '动作类型（1=新增赞, 2=新增踩, 3=取消赞, 4=取消踩, 5=改投赞→踩, 6=改投踩→赞）',
    `old_action` TINYINT DEFAULT NULL COMMENT '动作前状态（1=赞, 2=踩, null=无记录）',
    `event_time` DATETIME NOT NULL COMMENT '事件发生时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_product_id` (`product_id`),
    KEY `idx_event_time` (`event_time`),
    KEY `idx_action` (`action`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评价事件日志表';
