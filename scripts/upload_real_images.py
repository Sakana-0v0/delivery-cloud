import os
import urllib.request, urllib.error, hmac, hashlib
from datetime import datetime, timezone
from urllib.parse import quote

MINIO_HOST = "127.0.0.1:9000"
ACCESS_KEY = "admin"
SECRET_KEY = "sakana013"
ASSETS_DIR = r"E:\VSCode_workspace\Delivery\assets"
BUCKET = "product-picture"

PRODUCTS = {
    1:  "500008.jpg", 2:  "500022.jpg", 3:  "500023.jpg", 4:  "500024.jpg",
    5:  "500025.jpg", 6:  "500026.jpg", 7:  "500033.jpg", 8:  "500034.jpg",
    9:  "500035.jpg", 10: "500036.jpg", 11: "500041.jpg", 12: "500042.jpg",
    13: "500043.jpg", 14: "500044.jpg", 15: "500045.jpg", 16: "500046.jpg",
    17: "500047.jpg", 18: "500038.jpg",
}

def sign_request(method, url_path, query_params=None, payload=b""):
    if isinstance(payload, str): payload = payload.encode("utf-8")
    now = datetime.now(timezone.utc)
    amz_date = now.strftime("%Y%m%dT%H%M%SZ")
    date_stamp = now.strftime("%Y%m%d")
    host = MINIO_HOST
    payload_hash = hashlib.sha256(payload).hexdigest()
    if query_params is None: query_params = {}
    canonical_querystring = "&".join(
        f"{quote(k, safe='')}={quote(v, safe='')}" for k, v in sorted(query_params.items())
    )
    canonical_request = "\n".join([method, url_path, canonical_querystring,
        f"host:{host}\nx-amz-content-sha256:{payload_hash}\nx-amz-date:{amz_date}\n",
        "host;x-amz-content-sha256;x-amz-date", payload_hash])
    algorithm = "AWS4-HMAC-SHA256"
    credential_scope = f"{date_stamp}/us-east-1/s3/aws4_request"
    hashed_canonical = hashlib.sha256(canonical_request.encode("utf-8")).hexdigest()
    string_to_sign = "\n".join([algorithm, amz_date, credential_scope, hashed_canonical])
    def sign(key, msg): return hmac.new(key, msg.encode("utf-8"), hashlib.sha256).digest()
    k_date = sign(("AWS4" + SECRET_KEY).encode("utf-8"), date_stamp)
    k_region = sign(k_date, "us-east-1")
    k_service = sign(k_region, "s3")
    k_signing = sign(k_service, "aws4_request")
    signature = hmac.new(k_signing, string_to_sign.encode("utf-8"), hashlib.sha256).hexdigest()
    return {
        "Authorization": f"{algorithm} Credential={ACCESS_KEY}/{credential_scope}, SignedHeaders=host;x-amz-content-sha256;x-amz-date, Signature={signature}",
        "x-amz-date": amz_date,
        "x-amz-content-sha256": payload_hash
    }, f"http://{host}{url_path}", canonical_querystring

# 先删除旧的 picsum 测试图
import re
print("===== 清理旧测试图 =====")
h, u, qs = sign_request("GET", f"/{BUCKET}/", {"list-type": "2", "max-keys": "100"})
url = f"{u}?{qs}" if qs else u
req = urllib.request.Request(url, headers=h, method="GET")
body = urllib.request.urlopen(req, timeout=10).read().decode("utf-8")
keys = re.findall(r"<Key>([^<]+)</Key>", body)
print(f"  当前有 {len(keys)} 个文件")
to_delete = [k for k in keys if k.startswith("product-") and k.endswith(".jpg")]
print(f"  准备删除 {len(to_delete)} 个 product-N.jpg")

# 批量删除
if to_delete:
    # 一次最多 1000 个对象
    delete_xml = '<?xml version="1.0" encoding="UTF-8"?><Delete><' + '/>'.join([f'Object><Key>{k}</Key></Object>' for k in to_delete]) + '></Delete>'
    h, u, qs = sign_request("POST", f"/{BUCKET}/", {"delete": ""}, payload=delete_xml.encode("utf-8"))
    url = f"{u}?{qs}" if qs else u
    req = urllib.request.Request(url, headers=h, method="POST", data=delete_xml.encode("utf-8"))
    req.add_header("Content-Type", "application/xml")
    try:
        resp = urllib.request.urlopen(req, timeout=30)
        print(f"  删除结果: {resp.status}")
        body = resp.read().decode("utf-8")
        # 检查删除结果
        errors = re.findall(r"<Error>.*?<Key>([^<]+)</Key>.*?<Message>([^<]+)</Message>", body)
        if errors:
            print(f"  ⚠️ 部分删除失败: {errors[:3]}")
        else:
            print(f"  ✅ 全部删除成功")
    except urllib.error.HTTPError as e:
        print(f"  ❌ 删除失败: {e.code}")

# 上传新图片
print("\n===== 上传真实菜品图片 =====")
success = 0
for pid, filename in PRODUCTS.items():
    file_path = os.path.join(ASSETS_DIR, filename)
    if not os.path.exists(file_path):
        print(f"  ❌ 文件不存在: {filename}")
        continue
    with open(file_path, "rb") as f:
        data = f.read()
    h, u, qs = sign_request("PUT", f"/{BUCKET}/{filename}", payload=data)
    url = f"{u}?{qs}" if qs else u
    req = urllib.request.Request(url, headers=h, method="PUT", data=data)
    req.add_header("Content-Type", "image/jpeg")
    try:
        resp = urllib.request.urlopen(req, timeout=30)
        print(f"  ✅ {filename} ({len(data)} bytes)")
        success += 1
    except urllib.error.HTTPError as e:
        print(f"  ❌ {filename}: {e.code}")

print(f"\n上传结果: {success}/{len(PRODUCTS)}")
