import pymysql
import random
from datetime import datetime, timedelta

random.seed(42)

DB_CONFIG = {
    "host": "localhost", "port": 3306, "user": "root",
    "password": "sakana013", "charset": "utf8mb4"
}

conn = pymysql.connect(database="del_product_db", **DB_CONFIG)
with conn.cursor() as cur:
    cur.execute("SELECT id, name, real_price, cover FROM t_product WHERE is_deleted=0")
    products = cur.fetchall()

new_user_ids = list(range(2087088327483966547, 2087088327483966646))

def weighted_status():
    r = random.random()
    cum = 0
    for status, w in [(2, 0.70), (1, 0.10), (3, 0.10), (4, 0.05), (5, 0.03), (6, 0.02)]:
        cum += w
        if r <= cum:
            return status
    return 2

def rand_dt_within(days=60):
    secs = random.randint(0, days * 86400)
    return datetime.now() - timedelta(seconds=secs)

# 清空 random state 并重新生成订单号
random.seed(42)
orders = []
for i in range(3):
    uid = random.choice(new_user_ids)
    n_items = random.randint(1, 5)
    selected = random.sample(products, n_items)
    status = weighted_status()
    ct = rand_dt_within(60)
    order_no = f"SIM{int(ct.timestamp())}{i:04d}"
    orders.append((order_no, uid, status, ct))

print("=== Generated orders ===")
for o in orders:
    print(f"  {o[0]} status={o[2]}")

conn = pymysql.connect(database="del_order_db", **DB_CONFIG)
with conn.cursor() as cur:
    cur.execute("SELECT order_no, status FROM t_order WHERE order_no LIKE 'SIM%' ORDER BY id LIMIT 3")
    print("\n=== DB first 3 (by id) ===")
    for r in cur.fetchall():
        print(f"  {r[0]} status={r[1]}")

print("\n=== Match check ===")
for o in orders:
    conn = pymysql.connect(database="del_order_db", **DB_CONFIG)
    with conn.cursor() as cur:
        cur.execute("SELECT 1 FROM t_order WHERE order_no = %s", (o[0],))
        found = cur.fetchone()
    print(f"  {o[0]}: in DB? {bool(found)}")