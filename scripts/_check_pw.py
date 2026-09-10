import bcrypt
import pymysql

DB_CONFIG = {
    "host": "localhost", "port": 3306, "user": "root",
    "password": "sakana013", "charset": "utf8mb4"
}

conn = pymysql.connect(database="del_user_db", **DB_CONFIG)
with conn.cursor() as cur:
    cur.execute(
        "SELECT username, password, CHAR_LENGTH(password) AS pw_len "
        "FROM t_user WHERE username = %s",
        ("sim_user_001",)
    )
    for r in cur.fetchall():
        print("username:", r[0])
        print("pw_len:", r[2])
        print("password (full):")
        print(" ", repr(r[1]))
        if r[1]:
            ok = bcrypt.checkpw(b"123456", r[1].encode("utf-8"))
            print("bcrypt.checkpw 123456:", ok)