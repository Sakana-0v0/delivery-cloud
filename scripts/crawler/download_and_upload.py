# -*- coding: utf-8 -*-
"""
Step 3 & 4: 下载图片并上传到 MinIO
功能：
  1. 从 dish_dictionary.json 读取待处理菜品
  2. 下载 TheMealDB 图片到本地临时目录
  3. 上传到 MinIO 并获取 URL
  4. 更新数据字典，保存最终结果
"""

import os
import json
import time
import argparse
import requests
from pathlib import Path
from datetime import datetime, timedelta
from concurrent.futures import ThreadPoolExecutor, as_completed
from urllib.parse import urlparse

# MinIO SDK
try:
    from minio import Minio
    from minio.error import S3Error
except ImportError:
    print("[!] 错误: 请先安装 minio SDK")
    print("    pip install minio")
    exit(1)


# 默认配置
DEFAULT_DICT_FILE = "dish_dictionary.json"
DEFAULT_OUTPUT_FILE = "dish_dictionary_final.json"
DEFAULT_TEMP_DIR = "temp_images"
DEFAULT_WORKERS = 5  # 并发线程数


def parse_minio_url(url: str) -> tuple:
    """解析 MinIO URL 获取 endpoint 和 bucket"""
    parsed = urlparse(url)
    return parsed.netloc, parsed.path.lstrip('/').split('/')[0] if '/' in parsed.path else ''


def download_image(url: str, output_path: Path, timeout: int = 30) -> bool:
    """下载图片到本地"""
    try:
        resp = requests.get(url, timeout=timeout)
        resp.raise_for_status()
        
        output_path.parent.mkdir(parents=True, exist_ok=True)
        with open(output_path, 'wb') as f:
            f.write(resp.content)
        return True
    except requests.RequestException as e:
        print(f"  [!] 下载失败: {url} - {e}")
        return False


def upload_to_minio(client: Minio, bucket: str, file_path: str, object_name: str) -> str:
    """上传文件到 MinIO"""
    try:
        # 确保 bucket 存在
        if not client.bucket_exists(bucket):
            client.make_bucket(bucket)
        
        # 上传文件
        client.fput_object(bucket, object_name, file_path)
        
        # 生成 presigned URL（7天有效期）
        url = client.presigned_get_object(bucket, object_name, expires=timedelta(days=7))
        return url
    except S3Error as e:
        print(f"  [!] MinIO 上传失败: {e}")
        return ""


def process_single_image(args: tuple, minio_client: Minio, bucket: str) -> dict:
    """处理单个图片（下载+上传）"""
    meal_id, info, temp_dir, base_url = args
    
    image_file = info.get('image_file', f'{meal_id}.jpg')
    local_path = Path(temp_dir) / image_file
    minio_url = ""
    
    # 构建原图 URL
    if info.get('_raw', {}).get('strMealThumb'):
        original_url = info['_raw']['strMealThumb']
    else:
        original_url = f"https://www.themealdb.com/images/media/meals/{image_file}"
    
    # 下载图片
    success = download_image(original_url, local_path)
    
    if success and local_path.exists():
        # 上传到 MinIO
        minio_url = upload_to_minio(minio_client, bucket, str(local_path), image_file)
        
        # 删除本地临时文件
        try:
            os.remove(local_path)
        except OSError:
            pass
    
    return {
        'meal_id': meal_id,
        'success': bool(minio_url),
        'minio_url': minio_url,
        'info': info
    }


def process_images(dict_file: str, output_file: str, temp_dir: str,
                    endpoint: str, access_key: str, secret_key: str,
                    bucket: str, workers: int = 5, secure: bool = False) -> int:
    """批量处理图片"""
    
    # 读取数据字典
    dict_path = Path(dict_file)
    if not dict_path.exists():
        print(f"[!] 错误: 找不到数据字典文件 {dict_file}")
        return 0
    
    print(f"[{datetime.now().strftime('%H:%M:%S')}] 读取数据字典: {dict_path.absolute()}")
    with open(dict_path, 'r', encoding='utf-8') as f:
        dictionary = json.load(f)
    
    # 过滤出需要处理的条目（已填写中文名和分类ID）
    to_process = []
    for meal_id, info in dictionary.items():
        if info.get('chinese_name') and info.get('category_id', 0) > 0:
            to_process.append((meal_id, info))
    
    if not to_process:
        print(f"[!] 没有找到需要处理的条目")
        print(f"    请确保数据字典中 chinese_name 和 category_id 已填写")
        return 0
    
    print(f"[{datetime.now().strftime('%H:%M:%S')}] 待处理: {len(to_process)} 条记录")
    print(f"[{datetime.now().strftime('%H:%M:%S')}] MinIO 配置: {endpoint}")
    print(f"[{datetime.now().strftime('%H:%M:%S')}] Bucket: {bucket}")
    print(f"[{datetime.now().strftime('%H:%M:%S')}] 并发数: {workers}")
    print("-" * 50)
    
    # 创建 MinIO 客户端
    try:
        minio_client = Minio(
            endpoint,
            access_key=access_key,
            secret_key=secret_key,
            secure=secure
        )
    except Exception as e:
        print(f"[!] MinIO 客户端创建失败: {e}")
        return 0
    
    # 创建临时目录
    Path(temp_dir).mkdir(parents=True, exist_ok=True)
    
    # 并发处理
    results = {}
    success_count = 0
    
    print(f"[{datetime.now().strftime('%H:%M:%S')}] 开始处理...")
    
    with ThreadPoolExecutor(max_workers=workers) as executor:
        futures = {}
        for meal_id, info in to_process:
            args = (meal_id, info, temp_dir, "")
            future = executor.submit(process_single_image, args, minio_client, bucket)
            futures[future] = meal_id
        
        for i, future in enumerate(as_completed(futures), 1):
            meal_id = futures[future]
            try:
                result = future.result()
                if result['success']:
                    success_count += 1
                    # 更新数据
                    info = result['info'].copy()
                    info['minio_url'] = result['minio_url']
                    info.pop('_raw', None)  # 移除原始数据
                    results[meal_id] = info
                    
                    name = info.get('chinese_name') or info['english_name']
                    print(f"[{datetime.now().strftime('%H:%M:%S')}] [{i}/{len(to_process)}] 成功: {name}")
                else:
                    results[meal_id] = result['info']
                    print(f"[{datetime.now().strftime('%H:%M:%S')}] [{i}/{len(to_process)}] 失败: {meal_id}")
            except Exception as e:
                print(f"[{datetime.now().strftime('%H:%M:%S')}] [{i}/{len(to_process)}] 异常: {meal_id} - {e}")
                results[meal_id] = dictionary.get(meal_id, {})
    
    # 保存结果
    output_path = Path(output_file)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    
    with open(output_path, 'w', encoding='utf-8') as f:
        json.dump(results, f, ensure_ascii=False, indent=2)
    
    # 清理临时目录
    try:
        import shutil
        if Path(temp_dir).exists():
            shutil.rmtree(temp_dir)
    except Exception:
        pass
    
    print("-" * 50)
    print(f"[{datetime.now().strftime('%H:%M:%S')}] 处理完成！")
    print(f"[{datetime.now().strftime('%H:%M:%S')}] 成功: {success_count}/{len(to_process)}")
    print(f"[{datetime.now().strftime('%H:%M:%S')}] 输出文件: {output_path.absolute()}")
    
    return success_count


def main():
    parser = argparse.ArgumentParser(description='下载菜品图片并上传到 MinIO')
    parser.add_argument('-i', '--input', default=DEFAULT_DICT_FILE,
                        help=f'数据字典文件 (默认: {DEFAULT_DICT_FILE})')
    parser.add_argument('-o', '--output', default=DEFAULT_OUTPUT_FILE,
                        help=f'输出文件 (默认: {DEFAULT_OUTPUT_FILE})')
    parser.add_argument('-t', '--temp-dir', default=DEFAULT_TEMP_DIR,
                        help=f'临时目录 (默认: {DEFAULT_TEMP_DIR})')
    parser.add_argument('--endpoint', default='127.0.0.1:9000',
                        help='MinIO endpoint (默认: 127.0.0.1:9000)')
    parser.add_argument('--access-key', default='admin',
                        help='MinIO access key (默认: admin)')
    parser.add_argument('--secret-key', default='sakana013',
                        help='MinIO secret key (默认: sakana013)')
    parser.add_argument('--bucket', default='product-picture',
                        help='MinIO bucket (默认: product-picture)')
    parser.add_argument('--secure', action='store_true',
                        help='使用 HTTPS 连接 MinIO')
    parser.add_argument('-w', '--workers', type=int, default=DEFAULT_WORKERS,
                        help=f'并发线程数 (默认: {DEFAULT_WORKERS})')
    
    args = parser.parse_args()
    
    process_images(
        dict_file=args.input,
        output_file=args.output,
        temp_dir=args.temp_dir,
        endpoint=args.endpoint,
        access_key=args.access_key,
        secret_key=args.secret_key,
        bucket=args.bucket,
        workers=args.workers,
        secure=args.secure
    )


if __name__ == '__main__':
    main()
