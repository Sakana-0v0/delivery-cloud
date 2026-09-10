# -*- coding: utf-8 -*-
"""探测心月互联 API 返回格式"""
import requests

TOKEN = 'dd1541f921c14cfc48827bc620e6256c'
BASE_URL = 'https://qq.wch666.com'

print('=' * 60)
print('测试 1: auth 接口（带 token）')
print('=' * 60)
url1 = f'{BASE_URL}/api/qq.php?token={TOKEN}&msg=test&display=pc'
print(f'URL: {url1}')
try:
    r1 = requests.get(url1, timeout=10, allow_redirects=False)
    print(f'Status: {r1.status_code}')
    print(f'Headers: {dict(r1.headers)}')
    print(f'Body (first 500):')
    print(r1.text[:500])
except Exception as e:
    print(f'Error: {e}')

print()
print('=' * 60)
print('测试 2: get_user_info 接口（用假 code）')
print('=' * 60)
url2 = f'{BASE_URL}/api/get_user_info.php?code=fake_code_for_test'
print(f'URL: {url2}')
try:
    r2 = requests.get(url2, timeout=10)
    print(f'Status: {r2.status_code}')
    print(f'Content-Type: {r2.headers.get("Content-Type")}')
    print(f'Headers: {dict(r2.headers)}')
    print(f'Body (raw):')
    print(repr(r2.text))
    print(f'Body (decoded):')
    print(r2.text)
except Exception as e:
    print(f'Error: {e}')

print()
print('=' * 60)
print('测试 3: get_user_info 无 code 参数')
print('=' * 60)
url3 = f'{BASE_URL}/api/get_user_info.php'
try:
    r3 = requests.get(url3, timeout=10)
    print(f'Status: {r3.status_code}')
    print(f'Content-Type: {r3.headers.get("Content-Type")}')
    print(f'Body: {r3.text}')
except Exception as e:
    print(f'Error: {e}')

print()
print('=' * 60)
print('测试 4: auth 接口无 token')
print('=' * 60)
url4 = f'{BASE_URL}/api/qq.php'
try:
    r4 = requests.get(url4, timeout=10, allow_redirects=False)
    print(f'Status: {r4.status_code}')
    print(f'Body: {r4.text}')
except Exception as e:
    print(f'Error: {e}')