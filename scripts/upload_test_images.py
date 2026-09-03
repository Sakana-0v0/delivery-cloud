"""
下载一张示例图片并上传到 MinIO product-picture bucket
"""
import urllib.request, urllib.error, hmac, hashlib
from datetime import datetime, timezone
import urllib.parse

access_key = "admin"
secret_key = "sakana013"

def uri_encode(s):
    return urllib.parse.quote(s, safe="/")  # 保留 / 用于路径

def sign_request(method, url_path, query_params=None, payload=b""):
    if isinstance(payload, str):
        payload = payload.encode("utf-8")
    now = datetime.now(timezone.utc)
    amz_date = now.strftime("%Y%m%dT%H%M%SZ")
    date_stamp = now.strftime("%Y%m%d")
    host = "127.0.0.1:9000"
    payload_hash = hashlib.sha256(payload).hexdigest()
    if query_params is None:
        query_params = {}
    canonical_querystring = "&".join(
        f"{uri_encode(k)}={uri_encode(v)}" for k, v in sorted(query_params.items())
    )
    canonical_request = "\n".join([method, url_path, canonical_querystring,
        f"host:{host}\nx-amz-content-sha256:{payload_hash}\nx-amz-date:{amz_date}\n",
        "host;x-amz-content-sha256;x-amz-date", payload_hash])
    algorithm = "AWS4-HMAC-SHA256"
    credential_scope = f"{date_stamp}/us-east-1/s3/aws4_request"
    hashed_canonical = hashlib.sha256(canonical_request.encode("utf-8")).hexdigest()
    string_to_sign = "\n".join([algorithm, amz_date, credential_scope, hashed_canonical])
    def sign(key, msg): return hmac.new(key, msg.encode("utf-8"), hashlib.sha256).digest()
    k_date = sign(("AWS4" + secret_key).encode("utf-8"), date_stamp)
    k_region = sign(k_date, "us-east-1")
    k_service = sign(k_region, "s3")
    k_signing = sign(k_service, "aws4_request")
    signature = hmac.new(k_signing, string_to_sign.encode("utf-8"), hashlib.sha256).hexdigest()
    return {
        "Authorization": f"{algorithm} Credential={access_key}/{credential_scope}, SignedHeaders=host;x-amz-content-sha256;x-amz-date, Signature={signature}",
        "x-amz-date": amz_date,
        "x-amz-content-sha256": payload_hash
    }, f"http://{host}{url_path}", canonical_querystring

# 从 Picsum 下载示例图片并上传
import os
os.makedirs("E:/Idea_project/delivery-cloud/test_images", exist_ok=True)

print("===== 下载测试图片 =====")
products = [
    (1, "https://picsum.photos/seed/1/400/400"),
    (2, "https://picsum.photos/seed/2/400/400"),
    (3, "https://picsum.photos/seed/3/400/400"),
    (4, "https://picsum.photos/seed/4/400/400"),
    (5, "https://picsum.photos/seed/5/400/400"),
]

for pid, url in products:
    try:
        req = urllib.request.Request(url)
        data = urllib.request.urlopen(req, timeout=30).read()
        print(f"  产品 {pid}: 下载了 {len(data)} 字节")
        
        # 上传到 MinIO
        object_name = f"product-{pid}.jpg"
        h, u, qs = sign_request("PUT", f"/product-picture/{object_name}", payload=data)
        url_with_qs = f"{u}?{qs}" if qs else u
        req = urllib.request.Request(url_with_qs, headers=h, method="PUT", data=data)
        req.add_header("Content-Type", "image/jpeg")
        try:
            resp = urllib.request.urlopen(req, timeout=30)
            print(f"    上传成功: {resp.status}")
        except urllib.error.HTTPError as e:
            print(f"    上传失败: {e.code}")
    except Exception as e:
        print(f"  产品 {pid} 失败: {e}")

print("\n===== 验证上传结果 =====")
h, u, qs = sign_request("GET", "/product-picture/", {"list-type": "2"})
url_with_qs = f"{u}?{qs}"
req = urllib.request.Request(url_with_qs, headers=h, method="GET")
body = urllib.request.urlopen(req, timeout=10).read().decode("utf-8")
import re
keys = re.findall(r"<Key>([^<]+)</Key>", body)
print(f"  product-picture 中现有 {len(keys)} 个文件")
for k in keys:
    print(f"    {k}")
