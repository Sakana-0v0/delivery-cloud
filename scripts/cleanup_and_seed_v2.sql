-- ============================================================
-- 测试数据清理 + 规范化生成脚本
-- ============================================================
-- 注意：执行前请先备份相关数据库
-- 用户: sakana (userId: 2087088327483965445)
-- ============================================================

-- 1. 清理旧地址



-- 2. 清理旧订单（外键约束）
DELETE FROM del_order_db.t_order_item WHERE order_id IN (
    SELECT id FROM del_order_db.t_order WHERE user_id = 2087088327483965445
);
DELETE FROM del_order_db.t_order WHERE user_id = 2087088327483965445;

-- 3. 清理 Redis 购物车（如需要）
-- FLUSHDB 0   # 删除 db 0 的所有数据（包括购物车）

-- 4. 插入规范地址（sakana 用户）
INSERT INTO del_user_db.t_user_address
    (user_id, receiver, phone, province, city, district, detail, is_default, is_deleted, create_time, update_time)
VALUES
    (2087088327483965445, '张三',  '13878789999', '湖南省', '衡阳市', '蒸湘区', '某街道123号', 1, 0, NOW(), NOW()),
    (2087088327483965445, '李四',  '13900008888', '广东省', '深圳市', '南山区', '科技园路1号', 0, 0, NOW(), NOW());

-- 5. 验证
SELECT id, receiver, phone, full_address FROM del_user_db.t_user_address
WHERE user_id = 2087088327483965445;
