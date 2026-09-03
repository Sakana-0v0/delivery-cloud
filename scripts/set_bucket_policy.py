import urllib.request, urllib.error, hmac, hashlib
from datetime import datetime, timezone

access_key = 'admin'
secret_key = 'sakana013'

def sign_request(method, url_path, query_string='', payload=''):
    now = datetime.now(timezone.utc)
    amz_date = now.strftime('%Y%m%dT%H%M%SZ')
    date_stamp = now.strftime('%Y%m%d')
    host = '127.0.0.1:9000'
    payload_hash = hashlib.sha256(payload.encode('utf-8')).hexdigest()
    canonical_request = '\n'.join([method, url_path, query_string,
        f'host:{host}\nx-amz-content-sha256:{payload_hash}\nx-amz-date:{amz_date}\n',
        'host;x-amz-content-sha256;x-amz-date', payload_hash])
    algorithm = 'AWS4-HMAC-SHA256'
    credential_scope = f'{date_stamp}/us-east-1/s3/aws4_request'
    hashed_canonical = hashlib.sha256(canonical_request.encode('utf-8')).hexdigest()
    string_to_sign = '\n'.join([algorithm, amz_date, credential_scope, hashed_canonical])
    def sign(key, msg): return hmac.new(key, msg.encode('utf-8'), hashlib.sha256).digest()
    k_date = sign(('AWS4' + secret_key).encode('utf-8'), date_stamp)
    k_region = sign(k_date, 'us-east-1')
    k_service = sign(k_region, 's3')
    k_signing = sign(k_service, 'aws4_request')
    signature = hmac.new(k_signing, string_to_sign.encode('utf-8'), hashlib.sha256).hexdigest()
    return {
        'Authorization': f'{algorithm} Credential={access_key}/{credential_scope}, SignedHeaders=host;x-amz-content-sha256;x-amz-date, Signature={signature}',
        'x-amz-date': amz_date,
        'x-amz-content-sha256': payload_hash
    }, f'http://{host}{url_path}'

# 1. 创建 product-picture bucket（如果不存在）
print('===== 1. 创建 product-picture bucket =====')
h, u = sign_request('PUT', '/product-picture')
req = urllib.request.Request(u, headers=h, method='PUT')
try:
    resp = urllib.request.urlopen(req, timeout=10)
    print(f'  状态: {resp.status}')
except urllib.error.HTTPError as e:
    print(f'  状态: {e.code}')

# 2. 设置 bucket policy 为 public read
print('\n===== 2. 设置 product-picture bucket policy =====')
policy = {
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Principal": {"AWS": ["*"]},
            "Action": ["s3:GetObject"],
            "Resource": ["arn:aws:s3:::product-picture/*"]
        }
    ]
}
import json
policy_json = json.dumps(policy)
h, u = sign_request('PUT', '/product-picture', 'policy=', payload=policy_json)
# 必须更新 payload_hash 用真实 payload
h['x-amz-content-sha256'] = hashlib.sha256(policy_json.encode('utf-8')).hexdigest()
# 重新签名（使用真实 payload）
h, u = sign_request('PUT', '/product-picture', 'policy=', payload=policy_json)

req = urllib.request.Request(u, headers=h, method='PUT', data=policy_json.encode('utf-8'))
try:
    resp = urllib.request.urlopen(req, timeout=10)
    print(f'  状态: {resp.status}')
except urllib.error.HTTPError as e:
    print(f'  状态: {e.code} - {e.reason}')
    print(f'  Body: {e.read().decode("utf-8")[:500]}')

# 3. 验证 public read 是否生效
print('\n===== 3. 验证 product-picture 是否可匿名访问 =====')
test_url = 'http://127.0.0.1:9000/product-picture/2026-09-01/0fae83da2b1fc73cd6242a335c9fe7dc.jpg'
try:
    req = urllib.request.Request(test_url, method='HEAD')
    resp = urllib.request.urlopen(req, timeout=10)
    print(f'  状态: {resp.status}, Content-Length: {resp.headers.get("Content-Length")}')
except urllib.error.HTTPError as e:
    print(f'  状态: {e.code} - {e.reason}')
