import bcrypt
import pymysql

DB_CONFIG = {
    "host": "localhost", "port": 3306, "user": "root",
    "password": "sakana013", "charset": "utf8mb4"
}

conn = pymysql.connect(database="del_user_db", **DB_CONFIG)
with conn.cursor() as cur:
    cur.execute("SELECT username, password FROM t_user WHERE username = %s LIMIT 3", ("sim_user_001",))
    rows = cur.fetchall()

print("=" * 60)
print("DB 中 sim_user_001 的密码 hash:")
for r in rows:
    print(f"  username: {r[0]}")
    print(f"  password (first 60 chars): {r[1][:60] if r[1] else None}...")

if rows and rows[0][1]:
    stored_hash = rows[0][1]
    test_password = "123456"
    is_valid = bcrypt.checkpw(test_password.encode("utf-8"), stored_hash.encode("utf-8"))
    print()
    print(f'验证密码 "{test_password}" 是否匹配: {is_valid}')

if rows and rows[0][1]:
    wrong = bcrypt.checkpw("wrong_password".encode("utf-8"), rows[0][1].encode("utf-8"))
    print(f"验证错误密码: {wrong}")