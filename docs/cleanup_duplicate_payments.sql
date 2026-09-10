-- ============================================================
-- 清理 t_payment 表中重复的支付记录
-- ============================================================
-- 注意：执行前请先备份！
-- 用户 sakana (userId: 2087088327483965445)
-- ============================================================

-- 1. 备份
CREATE TABLE t_payment_backup_20260903 AS SELECT * FROM t_payment;

-- 2. 查看重复数据
SELECT order_no, COUNT(*) as cnt, MIN(create_time) as earliest
FROM t_payment
GROUP BY order_no
HAVING cnt > 1;

-- 3. 删除重复记录（保留每组最早的一条，即 id 最小的）
DELETE p1 FROM t_payment p1
INNER JOIN t_payment p2
WHERE p1.order_no = p2.order_no
  AND p1.id > p2.id
  AND p1.status = 0;  -- 仅删除 PENDING 状态的重复

-- 4. 验证
SELECT order_no, COUNT(*) as cnt
FROM t_payment
GROUP BY order_no
HAVING cnt > 1;

-- 期望：返回 0 行
SELECT COUNT(*) AS total_payment_records FROM t_payment;
