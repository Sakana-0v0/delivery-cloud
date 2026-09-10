import random
from datetime import datetime, timedelta
import pymysql

random.seed(42)
ct = datetime.now() - timedelta(seconds=random.randint(0, 60*86400))
order_no = f"SIM{int(ct.timestamp())}{0:04d}"
print("Generated order_no:", repr(order_no))
print("int(ct.timestamp()):", int(ct.timestamp()))

# 查询 DB 看是否真存在
conn = pymysql.connect(database="del_order_db", **DB_CONFIG)
with conn.cursor() as cur:
    cur.execute("SELECT order_no FROM t_order WHERE order_no LIKE 'SIM%' LIMIT 5")
    rows = cur.fetchall()
    print("DB first 5 order_no:", [r[0] for r in rows])
    print("Equal?", [r[0] for r in rows][0] == order_no if rows else "no match")