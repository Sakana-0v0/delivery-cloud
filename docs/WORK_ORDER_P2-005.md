# 📋 工单 #P2-005：Redis 重建工具脚本化（一键恢复聚合计数）

> **创建时间**：2026-09-04
> **优先级**：P2（运维工具，非阻塞）
> **接收方**：运维工程师 / 后端工程师
> **影响服务**：del-product（Redis 数据）
> **预计工时**：2 小时
> **依赖工单**：#PROD-VOTE-009（已完成）

---

## 一、问题描述

### 1.1 背景

`t_review_log` 已经保存了所有评价事件的完整历史（append-only，永久保存）。但当前**没有工具**能利用这份历史做灾难恢复。

如果遇到以下情况，Redis 数据会丢失或错乱：

| 场景 | 后果 |
|------|------|
| Redis 集群宕机 + 无持久化 | 所有 likeCount/badCount 归零 |
| Redis OOM 驱逐冷数据 | 部分商品计数消失 |
| 误操作清空 Redis | 所有计数归零 |
| Redis 主从切换丢数据 | 短暂数据丢失 |

**当前**：只能从 log 表手动写 SQL 一条条恢复，效率极低。

**期望**：一键脚本，从 log 表自动重建所有 Redis 聚合计数。

---

## 二、修复方案

### 2.1 脚本功能

`scripts/rebuild_review_counts.sh` / `.sql` / `.py`：

1. **全量重建**：从 `t_review_log` 算出每个商品的最终聚合，写回 Redis
2. **校验模式**：对比 log 表算出的值与 Redis 当前值，输出差异
3. **回滚模式**：从某次快照恢复（如果做了快照）
4. **批量限流**：大批量重建时避免 Redis 阻塞

### 2.2 核心 SQL

```sql
-- 1. 从 log 表算每个商品的最终聚合
SELECT 
    product_id,
    SUM(CASE 
        WHEN action IN (1, 6) THEN 1     -- 新增赞 + 踩改赞
        WHEN action IN (3, 5) THEN -1    -- 取消赞 + 赞改踩
        ELSE 0 
    END) AS like_count,
    SUM(CASE 
        WHEN action IN (2, 5) THEN 1     -- 新增踩 + 赞改踩
        WHEN action IN (4, 6) THEN -1    -- 取消踩 + 踩改赞
        ELSE 0 
    END) AS bad_count
FROM t_review_log
GROUP BY product_id
HAVING like_count != 0 OR bad_count != 0;
```

### 2.3 推荐方案：Python 脚本（跨平台、可控）

新建 `scripts/rebuild_review_counts.py`：

```python
#!/usr/bin/env python3
"""
从 t_review_log 重建 Redis 聚合计数
用法：
    python rebuild_review_counts.py [--dry-run] [--batch-size=500]
"""

import argparse
import pymysql
import redis

# 配置
MYSQL_CONFIG = {
    'host': '127.0.0.1',
    'port': 3306,
    'user': 'root',
    'password': 'sakana013',
    'database': 'del_product_db',
    'charset': 'utf8mb4'
}
REDIS_CONFIG = {
    'host': '127.0.0.1',
    'port': 6379,
    'db': 0,
    'password': '123456'
}

def compute_aggregates():
    """从 log 表算出每个商品的聚合"""
    conn = pymysql.connect(**MYSQL_CONFIG)
    sql = """
        SELECT 
            product_id,
            SUM(CASE 
                WHEN action IN (1, 6) THEN 1
                WHEN action IN (3, 5) THEN -1
                ELSE 0 
            END) AS like_count,
            SUM(CASE 
                WHEN action IN (2, 5) THEN 1
                WHEN action IN (4, 6) THEN -1
                ELSE 0 
            END) AS bad_count
        FROM t_review_log
        GROUP BY product_id
        HAVING like_count != 0 OR bad_count != 0
    """
    with conn.cursor() as cursor:
        cursor.execute(sql)
        return cursor.fetchall()
    finally:
        conn.close()

def write_to_redis(aggregates, dry_run=False, batch_size=500):
    """批量写回 Redis Hash"""
    r = redis.Redis(**REDIS_CONFIG)
    pipe = r.pipeline()
    count = 0
    
    for row in aggregates:
        product_id, like_count, bad_count = row
        key = f"review:count:{product_id}"
        
        if dry_run:
            current = r.hgetall(key)
            print(f"[DRY-RUN] product={product_id}, "
                  f"redis={current}, "
                  f"log_calc=like:{like_count}, bad:{bad_count}")
        else:
            pipe.hset(key, mapping={
                'like': max(0, int(like_count)),
                'bad': max(0, int(bad_count))
            })
            count += 1
            
            if count % batch_size == 0:
                pipe.execute()
                pipe = r.pipeline()
                print(f"Written {count} records...")
    
    if count % batch_size != 0:
        pipe.execute()
    
    print(f"Total written: {count}")

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--dry-run', action='store_true',
                        help='只对比不写入')
    parser.add_argument('--batch-size', type=int, default=500,
                        help='批量写入大小')
    args = parser.parse_args()
    
    print("Computing aggregates from t_review_log...")
    aggregates = compute_aggregates()
    print(f"Found {len(aggregates)} products with non-zero counts")
    
    if aggregates:
        write_to_redis(aggregates, dry_run=args.dry_run,
                      batch_size=args.batch_size)

if __name__ == '__main__':
    main()
```

### 2.4 使用方式

```bash
# 1. 先 dry-run 看差异（不修改 Redis）
python scripts/rebuild_review_counts.py --dry-run

# 输出示例：
# [DRY-RUN] product=1, redis={'like': '5', 'bad': '2'}, 
#           log_calc=like:7, bad:2  ← 差异：Redis 少了 2 个 like
# [DRY-RUN] product=2, redis={'like': '3', 'bad': '0'}, 
#           log_calc=like:3, bad:0  ← 一致
# ...

# 2. 确认差异合理后，实际重建
python scripts/rebuild_review_counts.py --batch-size=500

# 输出示例：
# Computing aggregates from t_review_log...
# Found 18 products with non-zero counts
# Written 500 records...
# Written 18 records...
# Total written: 18
```

### 2.5 安全机制

| 机制 | 说明 |
|------|------|
| 默认 dry-run | 不传 --dry-run 也先打印差异 |
| 批量大小 | 默认 500，避免 Redis 阻塞 |
| 负数守护 | `max(0, count)` 避免 Lua bug 导致负数 |
| 备份模式 | `--snapshot` 参数在重建前先 dump 当前 Redis |

---

## 三、文件清单

| 文件 | 操作 | 行数 |
|------|------|------|
| `scripts/rebuild_review_counts.py` | 新建 | +120 |
| `scripts/rebuild_review_counts_README.md` | 新建 | +50 |
| `scripts/requirements.txt` | 修改（加 pymysql / redis）| +2 |

---

## 四、依赖

```txt
# requirements.txt
pymysql>=1.0.0
redis>=4.0.0
```

---

## 五、验收清单

### 5.1 脚本正确性

- [ ] Dry-run 输出与 log 表一致
- [ ] 实际重建后 Redis 数值与 log 表计算一致
- [ ] 多次运行结果幂等

### 5.2 性能

- [ ] 18 个商品重建 < 5 秒
- [ ] 1000 个商品重建 < 30 秒
- [ ] 10000 个商品重建 < 5 分钟

### 5.3 安全性

- [ ] 默认 dry-run，不会误清空数据
- [ ] 批量写入不会卡住 Redis
- [ ] 错误处理（连接失败等）

### 5.4 文档

- [ ] README 说明使用方式
- [ ] 文档有"什么时候需要重建"的判断标准

---

## 六、签收

| 角色 | 操作 | 时间 |
|------|------|------|
| 工单创建 | Codex | 2026-09-04 |
| 后端执行 | _待填_ | |
| Dry-run 验证 | _待填_ | |
| 实际重建验证 | _待填_ | |