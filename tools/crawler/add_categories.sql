-- 添加西式分类脚本
-- 用于在现有数据库中添加 3 个新分类
-- 使用方法：mysql -u root -p del_product_db < add_categories.sql

-- 检查现有分类
SELECT '现有分类:' AS info;
SELECT id, name FROM t_category ORDER BY id;

-- 添加西式主菜（ID=5）
INSERT IGNORE INTO t_category (id, name, sort, status) VALUES (5, '西式主菜', 5, 0);

-- 添加西式轻食（ID=6）
INSERT IGNORE INTO t_category (id, name, sort, status) VALUES (6, '西式轻食', 6, 0);

-- 添加甜点饮品（ID=7）
INSERT IGNORE INTO t_category (id, name, sort, status) VALUES (7, '甜点饮品', 7, 0);

-- 验证结果
SELECT '添加后分类:' AS info;
SELECT id, name, sort, status FROM t_category ORDER BY id;
