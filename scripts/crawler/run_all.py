# -*- coding: utf-8 -*-
"""
一键运行脚本
按顺序执行所有步骤：
  1. 爬取原始数据 (crawl_meal.py)
  2. 生成数据字典模板 (generate_dictionary.py)
  3. 批量处理数据字典 (batch_process.py)
  4. 下载图片并上传 MinIO (download_and_upload.py)
  5. 生成入库 SQL (generate_sql.py)
"""

import subprocess
import sys
from pathlib import Path
from datetime import datetime

# 配置区域（根据你的环境修改）============================
MINIO_ENDPOINT = "127.0.0.1:9000"
MINIO_ACCESS_KEY = "admin"
MINIO_SECRET_KEY = "sakana013"
MINIO_BUCKET = "product-picture"
MINIO_SECURE = False  # 是否使用 HTTPS

MIN_PRICE = 15.0
MAX_PRICE = 88.0
# ====================================================


def print_step(step_num: int, total: int, title: str):
    print()
    print("=" * 60)
    print(f"  Step {step_num}/{total}: {title}")
    print("=" * 60)


def run_script(script_name: str, args: list = None) -> bool:
    """运行 Python 脚本"""
    cmd = [sys.executable, script_name]
    if args:
        cmd.extend(args)
    
    print(f"[{datetime.now().strftime('%H:%M:%S')}] 运行: {' '.join(cmd)}")
    
    try:
        result = subprocess.run(cmd, check=True)
        return result.returncode == 0
    except subprocess.CalledProcessError as e:
        print(f"[!] 脚本执行失败: {e}")
        return False
    except FileNotFoundError:
        print(f"[!] 找不到脚本: {script_name}")
        return False


def main():
    print()
    print("╔" + "=" * 58 + "╗")
    print("║" + " " * 15 + "菜品数据爬取与处理流程" + " " * 15 + "║")
    print("╚" + "=" * 58 + "╝")
    
    script_dir = Path(__file__).parent
    total_steps = 5
    
    # Step 1: 爬取原始数据
    print_step(1, total_steps, "爬取 TheMealDB 原始数据")
    if not run_script(script_dir / "crawl_meal.py"):
        print("[!] Step 1 失败，退出")
        return
    
    # Step 2: 生成数据字典模板
    print_step(2, total_steps, "生成数据字典模板")
    if not run_script(script_dir / "generate_dictionary.py"):
        print("[!] Step 2 失败，退出")
        return
    
    # Step 3: 批量处理数据字典
    print_step(3, total_steps, "批量处理（翻译 + 价格 + 描述）")
    dict_file = script_dir / "dish_dictionary.json"
    if not run_script(script_dir / "batch_process.py", [
        "-i", str(dict_file),
        "-o", str(dict_file),
        "--min-price", str(MIN_PRICE),
        "--max-price", str(MAX_PRICE),
        "--seed", "42"  # 固定种子保证可复现
    ]):
        print("[!] Step 3 失败，退出")
        return
    
    # Step 4: 下载图片并上传 MinIO
    print_step(4, total_steps, "下载图片并上传到 MinIO")
    if not run_script(script_dir / "download_and_upload.py", [
        "-i", str(dict_file),
        "-o", str(script_dir / "dish_dictionary_final.json"),
        "--endpoint", MINIO_ENDPOINT,
        "--access-key", MINIO_ACCESS_KEY,
        "--secret-key", MINIO_SECRET_KEY,
        "--bucket", MINIO_BUCKET,
        "--secure" if MINIO_SECURE else "",
        "-w", "5"
    ]):
        print("[!] Step 4 失败，退出")
        return
    
    # Step 5: 生成入库 SQL
    print_step(5, total_steps, "生成入库 SQL")
    if not run_script(script_dir / "generate_sql.py", [
        "-i", str(script_dir / "dish_dictionary_final.json"),
        "-o", str(script_dir / "products_import.sql")
    ]):
        print("[!] Step 5 失败，退出")
        return
    
    # 完成
    print()
    print("=" * 60)
    print("  ✅ 全部完成！")
    print("=" * 60)
    print()
    print("生成的文件:")
    print(f"  - {script_dir / 'raw_dishes.jsonl'}")
    print(f"  - {script_dir / 'dish_dictionary.json'}")
    print(f"  - {script_dir / 'dish_dictionary_final.json'}")
    print(f"  - {script_dir / 'products_import.sql'}")
    print()
    print("下一步:")
    print("  1. 检查 dish_dictionary_final.json 确认数据正确")
    print("  2. 执行 SQL 导入: mysql -u root -p dbname < products_import.sql")
    print()


if __name__ == '__main__':
    main()
