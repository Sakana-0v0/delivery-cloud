-- ===========================================
-- 分类数据初始化脚本
-- 仅添加新增的西式分类，不影响现有数据
-- ===========================================
-- 运行前请确保数据库已存在，且 t_category 表已创建
-- ===========================================

-- 插入新增的西式分类（ID 5-7）
-- 注意：如果分类已存在会报错，可先删除或使用 INSERT IGNORE

-- 西式主菜（用于 Beef, Chicken, Lamb, Pork, Goat 等）
INSERT IGNORE INTO t_category (id, name, sort, status) VALUES (5, '\''西式主菜'\'', 5, 0);

-- 西式轻食（用于 Pasta, Seafood, Vegetarian, Vegan 等）
INSERT IGNORE INTO t_category (id, name, sort, status) VALUES (6, '\''西式轻食'\'', 6, 0);

-- 甜点饮品（用于 Dessert, Side, Starter 等）
INSERT IGNORE INTO t_category (id, name, sort, status) VALUES (7, '\''甜点饮品'\'', 7, 0);

-- 验证分类数据
SELECT id, name, sort, status FROM t_category ORDER BY id;

-- ===========================================
-- 分类映射说明（供爬虫脚本使用）
-- ===========================================
-- TheMealDB 分类 -> 数据库 category_id
-- 
-- Breakfast      -> 2  精品小炒
-- Miscellaneous  -> 2  精品小炒
-- Soup           -> 3  汤品
-- 
-- Beef           -> 5  西式主菜
-- Chicken        -> 5  西式主菜
-- Lamb           -> 5  西式主菜
-- Pork           -> 5  西式主菜
-- Goat           -> 5  西式主菜
-- Fish           -> 5  西式主菜
-- 
-- Pasta          -> 6  西式轻食
-- Seafood        -> 6  西式轻食
-- Vegetarian     -> 6  西式轻食
-- Vegan          -> 6  西式轻食
-- 
-- Dessert        -> 7  甜点饮品
-- Side           -> 7  甜点饮品
-- Starter        -> 7  甜点饮品
-- ===========================================
