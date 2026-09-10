# -*- coding: utf-8 -*-
"""测试心月互联 get_user_info 真实响应"""
import requests
import json

CODE = '06BB9A1E93C00232E4F7B3AD34C12FCC'
url = 'https://qq.wch666.com/api/get_user_info.php?code=' + CODE
print('URL:', url)
print()

resp = requests.get(url, timeout=10)
print('Status:', resp.status_code)
print('Content-Type:', resp.headers.get('Content-Type'))
print('Apparent Encoding:', resp.apparent_encoding)
print()

print('=== Body (raw repr) ===')
print(repr(resp.text))
print()

print('=== Body (decoded) ===')
print(resp.text)
print()
print('Length:', len(resp.text), 'chars')
print()

print('=== Try parse as JSON ===')
try:
    data = json.loads(resp.text)
    print('JSON OK!')
    print(json.dumps(data, ensure_ascii=False, indent=2))
except Exception as e:
    print('Not JSON:', e)

print()
print('=== Try parse as JSONP ===')
if resp.text.startswith('callback('):
    print('Looks like JSONP')
    inner = resp.text[9:-1]
    try:
        data = json.loads(inner)
        print('JSONP OK!')
        print(json.dumps(data, ensure_ascii=False, indent=2))
    except Exception as e:
        print('JSONP failed:', e)
else:
    print('Not JSONP (does not start with callback()')

print()
print('=== Try parse as query string ===')
if '=' in resp.text and '&' in resp.text and '{' not in resp.text:
    print('Looks like query string')
    from urllib.parse import parse_qs
    parsed = parse_qs(resp.text)
    print('Parsed:')
    for k, v in parsed.items():
        print(f'  {k}: {v}')
else:
    print('Not query string')