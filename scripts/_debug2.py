import random
import pymysql
from collections import Counter
from datetime import datetime, timedelta

random.seed(42)

DB_CONFIG = {
    'host': 'localhost', 'port': 3306, 'user': 'root',
    'password': 'sakana013', 'charset': 'utf8mb4',
}

# 清理
c = pymysql.connect(database='del_order_db', **DB_CONFIG)
with c.cursor() as cur:
    cur.execute("DELETE FROM t_order WHERE order_no LIKE %s", ("SIM%",))
c.commit(); c.close()
print("Cleaned")

# 加载商品
c = pymysql.connect(database='del_product_db', **DB_CONFIG)
with c.cursor() as cur:
    cur.execute("SELECT id, name, real_price, cover FROM t_product WHERE is_deleted=0")
    products = cur.fetchall()
c.close()
print(f"Products: {len(products)}")

new_user_ids = list(range(2087088327483966347, 2087088327483966447))

STATUS_WEIGHTS = [(2, 0.70), (1, 0.10), (3, 0.10), (4, 0.05), (5, 0.03), (6, 0.02)]

def weighted_status():
    r = random.random()
    cum = 0
    for status, w in STATUS_WEIGHTS:
        cum += w
        if r <= cum:
            return status
    return 2

def rand_dt_within(days=60):
    secs = random.randint(0, days * 86400)
    return datetime.now() - timedelta(seconds=secs)

# 生成订单（跟原脚本一样）
orders = []
for i in range(500):
    uid = random.choice(new_user_ids)
    n_items = random.randint(1, 5)
    selected = random.sample(products, n_items)
    status = weighted_status()
    ct = rand_dt_within(60)
    pt = ct + timedelta(minutes=random.randint(1, 30)) if status >= 2 else None
    st = pt + timedelta(hours=random.randint(2, 24)) if status >= 3 else None
    ft = st + timedelta(days=random.randint(1, 3)) if status >= 4 else None
    ct2 = ct + timedelta(hours=random.randint(1, 48)) if status in [5, 6] else None
    order_no = f"SIM{int(ct.timestamp())}{i:04d}"
    orders.append((order_no, uid, 99.0, 99.0, status, 'recv', 'phone', 'addr', None, pt, st, ft, ct2, ct))

print("Status distribution:", Counter(o[4] for o in orders))

# 插入
c = pymysql.connect(database='del_order_db', **DB_CONFIG)
with c.cursor() as cur:
    cur.executemany(
        """INSERT INTO t_order (order_no, user_id, total_amount, pay_amount, status,
           receiver, phone, address, remark, pay_time, ship_time, finish_time, cancel_time, create_time)
           VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)""",
        orders
    )
    c.commit()
c.close()

# 立刻查 DB
c = pymysql.connect(database='del_order_db', **DB_CONFIG)
with c.cursor() as cur:
    cur.execute("SELECT status, COUNT(*) FROM t_order WHERE order_no LIKE %s GROUP BY status", ("SIM%",))
    print("DB actual:", cur.fetchall())
c.close()