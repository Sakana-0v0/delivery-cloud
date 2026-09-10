
USE del_product_db;

-- 1. 重命名现有分类（修正语义）
UPDATE t_category SET name = '西式主菜', sort = 5 WHERE id = 5;
UPDATE t_category SET name = '家常小炒', sort = 2 WHERE id = 2;
UPDATE t_category SET name = '鲜汤靓煲', sort = 3 WHERE id = 3;
UPDATE t_category SET name = '米面主食', sort = 4 WHERE id = 4;
UPDATE t_category SET name = '甜品烘焙', sort = 7 WHERE id = 7;

-- 2. 添加新分类（ID 8-11）
INSERT IGNORE INTO t_category (id, name, sort, status) VALUES
  (8,  '风味小吃',  8,  0),
  (9,  '海鲜西餐',  9,  0),
  (10, '异国料理',  10, 0),
  (11, '茶饮果饮',  11, 0);

-- 3. 验证
SELECT id, name, sort FROM t_category ORDER BY id;