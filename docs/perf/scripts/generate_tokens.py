"""
离线生成 perf 测试用 JWT 列表（无需启动业务）。
算法与 del-gateway/JwtVerifier 一致（HS256），密钥来自 common-jwt.yml 的 user-pool-secret。

Usage:
    python generate_tokens.py --count 1000 --output tokens.csv
    python generate_tokens.py --count 100 --role ADMIN --output admin-tokens.csv

输出格式 CSV：userId,token
"""
import argparse
import base64
import hashlib
import hmac
import json
import sys
import time


def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode("ascii")


def make_jwt(user_id: int, username: str, role: str, secret: str, ttl_seconds: int = 7200) -> str:
    """生成与 JwtVerifier 兼容的 HS256 JWT"""
    header = {"alg": "HS256", "typ": "JWT"}
    now = int(time.time())
    payload = {
        "sub": str(user_id),
        "username": username,
        "role": role,
        "iat": now,
        "exp": now + ttl_seconds,
    }
    h = b64url(json.dumps(header, separators=(",", ":"), ensure_ascii=False).encode())
    p = b64url(json.dumps(payload, separators=(",", ":"), ensure_ascii=False).encode())
    signing_input = f"{h}.{p}".encode("ascii")
    sig = hmac.new(secret.encode("utf-8"), signing_input, hashlib.sha256).digest()
    s = b64url(sig)
    return f"{h}.{p}.{s}"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--count", type=int, default=1000, help="生成 token 数量")
    ap.add_argument("--user-prefix", default="perf_user", help="用户名/email 前缀")
    ap.add_argument("--role", default="USER", choices=["USER", "ADMIN"], help="角色")
    ap.add_argument("--secret",
                    default="del-user-c-end-secret-key-7sKpR9D2fQzN8vBcX5gHjL4mWtY6aUe0oIu3bSrF7dGhJkLqPwTyZr",
                    help="user-pool-secret（默认与 common-jwt.yml 一致）")
    ap.add_argument("--ttl", type=int, default=7200, help="过期秒数")
    ap.add_argument("--output", default="tokens.csv", help="输出 CSV 路径")
    args = ap.parse_args()

    with open(args.output, "w", encoding="utf-8", newline="\n") as f:
        f.write("userId,token\n")
        for i in range(1, args.count + 1):
            uid = i
            username = f"{args.user_prefix}_{i}"
            token = make_jwt(uid, username, args.role, args.secret, args.ttl)
            f.write(f"{uid},{token}\n")
    print(f"[generate_tokens] wrote {args.count} tokens to {args.output}", file=sys.stderr)


if __name__ == "__main__":
    main()