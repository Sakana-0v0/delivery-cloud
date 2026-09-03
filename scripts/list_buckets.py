import urllib.request, urllib.error, hmac, hashlib, re
from datetime import datetime, timezone
import urllib.parse

access_key = "admin"
secret_key = "sakana013"

def uri_encode(s):
    return urllib.parse.quote(s, safe="")

def sign_request(method, url_path, query_params=None, payload=""):
    now = datetime.now(timezone.utc)
    amz_date = now.strftime("%Y%m%dT%H%M%SZ")
    date_stamp = now.strftime("%Y%m%d")
    host = "127.0.0.1:9000"
    payload_hash = hashlib.sha256(payload.encode("utf-8")).hexdigest()
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

for bucket in ["res", "product-picture"]:
    print(f"===== 列出 {bucket} bucket =====")
    h, u, qs = sign_request("GET", f"/{bucket}/", {"list-type": "2", "max-keys": "100"})
    url = f"{u}?{qs}" if qs else u
    req = urllib.request.Request(url, headers=h, method="GET")
    try:
        body = urllib.request.urlopen(req, timeout=10).read().decode("utf-8")
        keys = re.findall(r"<Key>([^<]+)</Key>", body)
        print(f"  {len(keys)} 个文件:")
        for k in keys[:10]:
            print(f"    {k}")
        if len(keys) > 10:
            print(f"    ... 还有 {len(keys)-10} 个")
    except urllib.error.HTTPError as e:
        print(f"  状态: {e.code} - {e.reason}")
