import random
import pymysql
from collections import Counter

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

# 加载商品
c = pymysql.connect(database='del_product_db', **DB_CONFIG)
with c.cursor() as cur:
    cur.execute("SELECT id, name, real_price, cover FROM t_product WHERE is_deleted=0")
    products = cur.fetchall()
c.close()

STATUS_WEIGHTS = [(2, 0.70), (1, 0.10), (3, 0.10), (4, 0.05), (5, 0.03), (6, 0.02)]

def weighted_status():
    r = random.random()
    cum = 0
    for status, w in STATUS_WEIGHTS:
        cum += w
        if r <= cum:
            return status
    return 2

new_user_ids = list(range(2087088327483966347, 2087088327483966447))

statuses = []
for _ in range(500):
    statuses.append(weighted_status())

print("订单状态分布:", Counter(statuses))