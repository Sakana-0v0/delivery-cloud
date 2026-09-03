import urllib.request, urllib.error, hmac, hashlib, re
from datetime import datetime, timezone
import urllib.parse

access_key = "admin"
secret_key = "sakana013"

def uri_encode(s):
    return urllib.parse.quote(s, safe="/")

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

# 上传 18 张图片
for pid in range(1, 19):
    object_name = f"product-{pid}.jpg"
    try:
        # 尝试先检查文件是否已存在
        h, u, qs = sign_request("HEAD", f"/product-picture/{object_name}")
        url = f"{u}"
        req = urllib.request.Request(url, headers=h, method="HEAD")
        try:
            resp = urllib.request.urlopen(req, timeout=5)
            print(f"  产品 {pid}: 已存在 ({resp.status})")
            continue
        except urllib.error.HTTPError:
            pass
        
        # 下载并上传
        pic_url = f"https://picsum.photos/seed/{pid}/400/400"
        data = urllib.request.urlopen(pic_url, timeout=30).read()
        h, u, qs = sign_request("PUT", f"/product-picture/{object_name}", payload=data)
        url_with_qs = f"{u}?{qs}" if qs else u
        req = urllib.request.Request(url_with_qs, headers=h, method="PUT", data=data)
        req.add_header("Content-Type", "image/jpeg")
        resp = urllib.request.urlopen(req, timeout=30)
        print(f"  产品 {pid}: 上传成功 ({resp.status}, {len(data)} bytes)")
    except Exception as e:
        print(f"  产品 {pid}: 失败 - {e}")
