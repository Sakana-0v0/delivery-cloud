import pymysql

conn = pymysql.connect(database='del_order_db', host='localhost', port=3306, user='root', password='sakana013', charset='utf8mb4')
with conn.cursor() as cur:
    cur.execute("SELECT order_no, id FROM t_order WHERE order_no LIKE 'SIM%' LIMIT 3")
    db_first_3 = cur.fetchall()
    cur.execute("SELECT order_no, id FROM t_order WHERE order_no LIKE 'SIM%' ORDER BY id DESC LIMIT 3")
    db_last_3 = cur.fetchall()

print("DB first 3 (oldest, by id):", db_first_3)
print("DB last 3 (newest, by id):", db_last_3)