import pymysql
import random
from datetime import datetime, timedelta

# 模拟脚本完整流程
random.seed(42)
DB_CONFIG = {"host": "localhost", "port": 3306, "user": "root", "password": "sakana013", "charset": "utf8mb4"}

conn_p = pymysql.connect(database="del_product_db", **DB_CONFIG)
with conn_p.cursor() as cur:
    cur.execute("SELECT id, name, real_price, cover FROM t_product WHERE is_deleted=0")
    products = cur.fetchall()

# 加载用户
new_user_ids = list(range(2087088327483966547, 2087088327483966646))
print(f"new_user_ids count: {len(new_user_ids)}")

# 模拟完整订单生成 500 条
orders = []
for i in range(500):
    uid = random.choice(new_user_ids)
    n_items = random.randint(1, 5)
    selected = random.sample(products, n_items)
    status = random.choices([2, 1, 3, 4, 5, 6], weights=[0.70, 0.10, 0.10, 0.05, 0.03, 0.02])[0]
    secs = random.randint(0, 60*86400)
    ct = datetime.now() - timedelta(seconds=secs)
    order_no = f"SIM{int(ct.timestamp())}{i:04d}"
    orders.append((order_no, uid, status, ct))

print(f"Generated orders count: {len(orders)}")
print(f"Sample order_nos: {orders[0][0]}, {orders[1][0]}, {orders[2][0]}")

# 模拟 order_id_map（实际是 DB SELECT 出来的）
order_id_map = {o[0]: i + 1 for i, o in enumerate(orders)}

# 检查匹配
matched = sum(1 for o in orders if o[0] in order_id_map)
print(f"Matched orders: {matched} / {len(orders)}")

# 调试：对比 order_meta 大小
order_meta = {ono: {"status": o[2], "uid": o[1]} for o in orders if o[0] in order_id_map}
print(f"order_meta size: {len(order_meta)}")