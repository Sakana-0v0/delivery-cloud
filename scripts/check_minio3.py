import urllib.request, urllib.error, hmac, hashlib, re
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

# 直接尝试访问具体文件（HEAD 请求）
file_path = '/res/2026-09-01/0fae83da2b1fc73cd6242a335c9fe7dc.jpg'
h, u = sign_request('GET', file_path)
req = urllib.request.Request(u, headers=h, method='HEAD')
try:
    resp = urllib.request.urlopen(req, timeout=10)
    print(f'res 文件: {resp.status}, Content-Length: {resp.headers.get("Content-Length")}')
except urllib.error.HTTPError as e:
    print(f'res 文件: {e.code}')

# 尝试 product-picture
file_path = '/product-picture/2026-09-01/0fae83da2b1fc73cd6242a335c9fe7dc.jpg'
h, u = sign_request('GET', file_path)
req = urllib.request.Request(u, headers=h, method='HEAD')
try:
    resp = urllib.request.urlopen(req, timeout=10)
    print(f'product-picture 文件: {resp.status}')
except urllib.error.HTTPError as e:
    print(f'product-picture 文件: {e.code} - {e.reason}')

# 尝试访问不同的 prefix
for prefix in ['', 'product-picture/', 'res/', '2026-09-01/', 'pics/', 'image/', 'images/']:
    h, u = sign_request('GET', f'/{prefix}', 'list-type=2&max-keys=3')
    req = urllib.request.Request(u, headers=h, method='GET')
    try:
        body = urllib.request.urlopen(req, timeout=5).read().decode('utf-8')
        keys = re.findall(r'<Key>([^<]+)</Key>', body)
        if keys:
            print(f'/{prefix} 找到文件: {keys[:3]}')
    except urllib.error.HTTPError as e:
        pass
