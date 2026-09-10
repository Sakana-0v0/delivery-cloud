import pymysql
from datetime import datetime

DB_CONFIG = {'host': 'localhost', 'port': 3306, 'user': 'root',
             'password': 'sakana013', 'charset': 'utf8mb4'}

conn = pymysql.connect(database='del_order_db', **DB_CONFIG)
with conn.cursor() as cur:
    cur.execute("SELECT id, status, pay_time, user_id FROM t_order WHERE order_no LIKE 'SIM%'")
    orders = cur.fetchall()
    cur.execute("SELECT order_id, product_id FROM t_order_item WHERE order_id IN (SELECT id FROM t_order WHERE order_no LIKE 'SIM%')")
    items = cur.fetchall()

print("orders:", len(orders))
print("items:", len(items))

now = datetime.now()
likable = 0
for o in orders:
    oid, status, pt, uid = o
    if status in [3, 4]:
        likable += sum(1 for it in items if it[0] == oid)
    elif status == 2 and pt and (now - pt).days > 7:
        likable += sum(1 for it in items if it[0] == oid)
print("likable:", likable)