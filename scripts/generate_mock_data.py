# -*- coding: utf-8 -*-
"""
Mock Data Generator for Delivery Project - v2

设计要点（基于 #3.6 数据设计讨论）：
- 编号昵称 User_001，无中文避免编码问题
- 价格 ±10% 随机
- 付款时间 1-240 分钟
- 赞踩扩展到 SHIPPED + COMPLETED + PAID 超 7 天
"""
import random
from datetime import datetime, timedelta
from collections import Counter

import pymysql

random.seed(42)

# ==================== 配置 ====================
DB_CONFIG = {
    "host": "localhost", "port": 3306, "user": "root",
    "password": "sakana013", "charset": "utf8mb4",
}

USER_COUNT = 100
ORDER_COUNT = 500
ITEM_MIN, ITEM_MAX = 1, 5
LIKE_TARGET = 1500  # 目标点赞踩数量

STATUS_WEIGHTS = [
    (2, 0.70),  # PAID
    (1, 0.10),  # PENDING
    (3, 0.10),  # SHIPPED
    (4, 0.05),  # COMPLETED
    (5, 0.03),  # CANCELLED
    (6, 0.02),  # REFUNDED
]

# 英文地址池（避开编码问题）
PROVINCES = ["Beijing", "Shanghai", "Guangdong", "Jiangsu", "Zhejiang",
             "Sichuan", "Hubei", "Hunan", "Fujian", "Shandong"]
CITIES = {
    "Beijing": ["Beijing"], "Shanghai": ["Shanghai"],
    "Guangdong": ["Shenzhen", "Guangzhou", "Dongguan", "Foshan"],
    "Jiangsu": ["Nanjing", "Suzhou", "Wuxi"],
    "Zhejiang": ["Hangzhou", "Ningbo", "Wenzhou"],
    "Sichuan": ["Chengdu", "Mianyang"],
    "Hubei": ["Wuhan"],
    "Hunan": ["Changsha"],
    "Fujian": ["Xiamen", "Fuzhou"],
    "Shandong": ["Jinan", "Qingdao"],
}
STREETS = ["Zhongshan Rd", "Renmin Rd", "Jiefang Rd", "Wenhua St",
           "Kejiyuan", "Shangye St", "Xueyuan Rd", "Jianshe Rd",
           "Heping Ave", "Guangming St", "Chang'an Ave", "Fuxing Rd"]
DISTRICTS = ["Haidian", "Chaoyang", "Dongcheng", "Xicheng", "Fengtai",
             "Pudong", "Minhang", "Bao'an", "Nanshan", "Tianhe",
             "Yuexiu", "Wuhou", "Qingyang", "Yuhua"]

def rand_dt_within(days=60):
    secs = random.randint(0, days * 86400)
    return datetime.now() - timedelta(seconds=secs)

def rand_phone():
    return f"138{random.randint(10000000, 99999999):08d}"

def weighted_status():
    r = random.random()
    cum = 0
    for status, w in STATUS_WEIGHTS:
        cum += w
        if r <= cum:
            return status
    return 2

def random_price_with_variance(base_price: float, variance: float = 0.1) -> float:
    """价格在基础价 ±10% 范围内随机"""
    delta = random.uniform(-variance, variance)
    new_price = base_price * (1 + delta)
    return round(max(new_price, 0.01), 2)

def batch_insert(cursor, sql, rows, batch_size=500):
    for i in range(0, len(rows), batch_size):
        cursor.executemany(sql, rows[i:i+batch_size])

# ==================== 主流程 ====================
print("=" * 60)
print("Mock Data Generator v2 - Delivery Project")
print("=" * 60)

# 0. 清理旧 mock 数据
print("\n[0/5] 清理旧的 mock 数据...")
c1 = pymysql.connect(database="del_user_db", **DB_CONFIG)
with c1.cursor() as cur:
    cur.execute("DELETE FROM t_user_address WHERE user_id IN (SELECT id FROM t_user WHERE username LIKE %s)", ("sim_user_%",))
    cur.execute("DELETE FROM t_user WHERE username LIKE %s", ("sim_user_%",))
c1.commit(); c1.close()

c2 = pymysql.connect(database="del_order_db", **DB_CONFIG)
with c2.cursor() as cur:
    cur.execute("DELETE FROM t_order WHERE order_no LIKE %s", ("SIM%",))
c2.commit(); c2.close()

c3 = pymysql.connect(database="del_product_db", **DB_CONFIG)
with c3.cursor() as cur:
    cur.execute("DELETE FROM t_review WHERE order_id IN (SELECT id FROM del_order_db.t_order WHERE order_no LIKE %s)", ("SIM%",))
c3.commit(); c3.close()
print("      - 已清理旧数据")

# 1. 加载商品
print("\n[1/5] 加载商品...")
conn_p = pymysql.connect(database="del_product_db", **DB_CONFIG)
with conn_p.cursor() as cur:
    cur.execute("SELECT id, name, real_price, cover FROM t_product WHERE is_deleted=0")
    products = cur.fetchall()
print(f"      - 商品: {len(products)} 个")
if len(products) < 10:
    print("ERROR: 商品太少")
    exit(1)

# 2. 生成用户（编号昵称，避免编码问题）
print(f"\n[2/5] 生成 {USER_COUNT} 个用户 + 地址...")
conn_u = pymysql.connect(database="del_user_db", **DB_CONFIG)

users = []
for i in range(1, USER_COUNT + 1):
    # 编号昵称：User_001
    nickname = f"User_{i:03d}"
    users.append((
        f"sim_user_{i:03d}",
        nickname,
        rand_phone(),
        f"sim_user_{i:03d}@test.com",
        f"https://api.dicebear.com/7.x/avataaars/svg?seed=user{i}",
        1,
    ))

with conn_u.cursor() as cur:
    batch_insert(cur,
        "INSERT INTO t_user (username, nickname, phone, email, avatar, status) VALUES (%s,%s,%s,%s,%s,%s)",
        users)
    conn_u.commit()
    cur.execute("SELECT id FROM t_user WHERE username LIKE 'sim_user_%' ORDER BY id")
    new_user_ids = [row[0] for row in cur.fetchall()]
print(f"      - 新增用户 {len(new_user_ids)} 个 (ID: {new_user_ids[0]}~{new_user_ids[-1]})")

# 生成地址（英文 province/city，避免编码问题）
addresses = []
for uid in new_user_ids:
    n_addr = random.randint(1, 2)
    for j in range(n_addr):
        prov = random.choice(PROVINCES)
        city = random.choice(CITIES[prov])
        # 收件人也用编号，避免编码问题
        receiver = f"User_{uid % 1000:03d}" if uid < 1000 else f"User_{uid}"
        addresses.append((
            uid, receiver, rand_phone(), prov, city,
            random.choice(DISTRICTS),
            f"No.{random.randint(1, 300)}, {random.choice(STREETS)}, Bldg {random.randint(1, 20)}, Room {random.randint(101, 2099)}",
            1 if j == 0 else 0,
        ))

with conn_u.cursor() as cur:
    batch_insert(cur,
        "INSERT INTO t_user_address (user_id, receiver, phone, province, city, district, detail, is_default) "
        "VALUES (%s,%s,%s,%s,%s,%s,%s,%s)",
        addresses)
    conn_u.commit()
print(f"      - 新增地址 {len(addresses)} 条")

# 读取默认地址映射
user_addresses = {}
with conn_u.cursor() as cur:
    cur.execute("""SELECT user_id, receiver, phone, CONCAT(province, ', ', city, ', ', district, ', ', detail)
                   FROM t_user_address WHERE user_id IN %s AND is_default=1 AND is_deleted=0""",
                (tuple(new_user_ids),))
    for uid, recv, phone, addr in cur.fetchall():
        user_addresses[uid] = {"receiver": recv, "phone": phone, "address": addr}

# 3. 生成订单（价格 ±10%、付款 1-240 分钟）
print(f"\n[3/5] 生成 {ORDER_COUNT} 个订单 + 订单项...")
conn_o = pymysql.connect(database="del_order_db", **DB_CONFIG)

orders = []
order_items_flat = []

for i in range(ORDER_COUNT):
    uid = random.choice(new_user_ids)
    if uid not in user_addresses:
        continue
    addr = user_addresses[uid]

    n_items = random.randint(ITEM_MIN, ITEM_MAX)
    selected = random.sample(products, n_items)

    items = []
    total = 0.0
    for p in selected:
        qty = random.randint(1, 3)
        # 价格 ±10% 随机
        price = random_price_with_variance(float(p[2]), 0.1)
        sub = round(price * qty, 2)
        items.append((p[0], p[1], p[3], price, qty, sub))
        total += sub
    total = round(total, 2)

    status = weighted_status()
    ct = rand_dt_within(60)
    # 付款时间：1-240 分钟（4 小时）更真实
    pt = ct + timedelta(minutes=random.randint(1, 240)) if status >= 2 else None
    st = pt + timedelta(hours=random.randint(2, 24)) if status >= 3 else None
    ft = st + timedelta(days=random.randint(1, 3)) if status >= 4 else None
    ct2 = ct + timedelta(hours=random.randint(1, 48)) if status in [5, 6] else None

    order_no = f"SIM{int(ct.timestamp())}{i:04d}"
    remark = random.choice([None, "Less spicy", "More spicy", "No cilantro", "Fast delivery"]) if random.random() > 0.6 else None

    orders.append((
        order_no, uid, total, total, status,
        addr["receiver"], addr["phone"], addr["address"], remark,
        pt, st, ft, ct2, ct
    ))
    for item in items:
        order_items_flat.append((order_no, *item))

# 插入订单
print(f"      - 正在插入 {len(orders)} 条订单...")
with conn_o.cursor() as cur:
    batch_insert(cur,
        """INSERT INTO t_order (order_no, user_id, total_amount, pay_amount, status,
           receiver, phone, address, remark, pay_time, ship_time, finish_time, cancel_time, create_time)
           VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)""",
        orders)
    conn_o.commit()
    cur.execute("SELECT id, order_no FROM t_order WHERE order_no LIKE 'SIM%'")
    order_id_map = {ono: oid for oid, ono in cur.fetchall()}

# 状态分布
status_counts = Counter(o[4] for o in orders)
print(f"      - 状态分布:")
status_names = {1: "PENDING", 2: "PAID", 3: "SHIPPED", 4: "COMPLETED", 5: "CANCELLED", 6: "REFUNDED"}
for s in sorted(status_counts.keys()):
    pct = status_counts[s] * 100 // len(orders)
    print(f"        {status_names[s]:12s} {status_counts[s]:4d} 条 ({pct}%)")

# 插入订单项
print(f"      - 正在插入订单项...")
items_with_id = []
for ono, *it in order_items_flat:
    if ono in order_id_map:
        items_with_id.append((order_id_map[ono],) + tuple(it))
print(f"      - 订单项 {len(items_with_id)} 条")

with conn_o.cursor() as cur:
    batch_insert(cur,
        """INSERT INTO t_order_item (order_id, product_id, product_name, product_cover, price, quantity, subtotal)
           VALUES (%s,%s,%s,%s,%s,%s,%s)""",
        items_with_id)
    conn_o.commit()


# 4. 生成点赞/踩（扩展范围：SHIPPED+COMPLETED+PAID超7天）
print(f"\n[4/5] 生成点赞/踩（目标 {LIKE_TARGET} 条）...")
conn_r = pymysql.connect(database="del_product_db", **DB_CONFIG)

likes = []
used = set()

order_items_by_order = {}
for item in items_with_id:
    order_items_by_order.setdefault(item[0], []).append(item)

order_meta = {}
for o in orders:
    if o[0] in order_id_map:
        order_meta[o[0]] = {"status": o[4], "pt": o[9], "uid": o[1]}

now = datetime.now()
likable_orders = []
for ono, meta in order_meta.items():
    if meta["status"] in [3, 4]:
        oid = order_id_map[ono]
        for item in order_items_by_order.get(oid, []):
            likable_orders.append((meta["uid"], oid, item[1]))
    elif meta["status"] == 2 and meta["pt"] and (now - meta["pt"]).days > 7:
        oid = order_id_map[ono]
        for item in order_items_by_order.get(oid, []):
            likable_orders.append((meta["uid"], oid, item[1]))

print(f"      - 可点赞订单项: {len(likable_orders)}")

attempts = 0
while len(likes) < LIKE_TARGET and attempts < LIKE_TARGET * 5:
    attempts += 1
    if not likable_orders:
        break
    uid, oid, pid = random.choice(likable_orders)
    key = (uid, oid, pid)
    if key in used:
        continue
    used.add(key)
    type_ = random.choices([1, 2], weights=[0.8, 0.2])[0]
    likes.append((uid, oid, pid, type_))

print(f"      - 生成 {len(likes)} 条 ({sum(1 for x in likes if x[3]==1)} 赞 / {sum(1 for x in likes if x[3]==2)} 踩)")

with conn_r.cursor() as cur:
    batch_insert(cur,
        "INSERT INTO t_review (user_id, order_id, product_id, type) VALUES (%s,%s,%s,%s)",
        likes)
    conn_r.commit()

# 5. 最终统计
print(f"\n[5/5] 完成！")
print("\n" + "=" * 60)
print("数据库最终状态")
print("=" * 60)

with conn_u.cursor() as cur:
    cur.execute("SELECT COUNT(*) FROM t_user WHERE username LIKE 'sim_user_%' AND is_deleted=0")
    u = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM t_user_address WHERE user_id IN (SELECT id FROM t_user WHERE username LIKE 'sim_user_%') AND is_deleted=0")
    a = cur.fetchone()[0]
print(f"  - 新增用户: {u}")
print(f"  - 新增地址: {a}")

with conn_o.cursor() as cur:
    cur.execute("SELECT COUNT(*) FROM t_order WHERE order_no LIKE 'SIM%'")
    o = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM t_order_item WHERE order_id IN (SELECT id FROM t_order WHERE order_no LIKE 'SIM%')")
    i = cur.fetchone()[0]
print(f"  - 新增订单: {o}")
print(f"  - 新增订单项: {i}")

with conn_r.cursor() as cur:
    cur.execute("SELECT COUNT(*) FROM t_review WHERE order_id IN (SELECT id FROM del_order_db.t_order WHERE order_no LIKE 'SIM%')")
    r = cur.fetchone()[0]
print(f"  - 新增点赞/踩: {r}")

conn_u.close(); conn_o.close(); conn_p.close(); conn_r.close()
print("\nDone!")
