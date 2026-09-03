# -*- coding: utf-8 -*-
"""
菜品数据爬虫 - TheMealDB
使用内置库(urllib)，生成 SQL 脚本
"""

import json
import random
import uuid
import urllib.request
import urllib.parse
import urllib.error
import time
import os
from datetime import datetime
from category_mapping import CATEGORY_MAPPING, ALL_CATEGORIES

# API 配置
BASE_URL = "https://www.themealdb.com/api/json/v1/1"
OUTPUT_DIR = "scripts/crawler/data"

# 字母列表
LETTERS = list('abcdefghijklmnopqrstuvwxyz')


def fetch_url(url: str, params: dict = None) -> dict:
    """使用 urllib 获取数据"""
    if params:
        url = f"{url}?{urllib.parse.urlencode(params)}"
    
    try:
        req = urllib.request.Request(url, headers={
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
        })
        with urllib.request.urlopen(req, timeout=30) as response:
            data = response.read().decode('utf-8')
            return json.loads(data)
    except urllib.error.HTTPError as e:
        print(f"  [HTTP错误] {e.code}: {e.reason}")
    except urllib.error.URLError as e:
        print(f"  [URL错误] {e.reason}")
    except Exception as e:
        print(f"  [错误] {e}")
    return {}


def get_meals_by_letter(letter: str) -> list:
    """按首字母搜索菜品"""
    data = fetch_url(f"{BASE_URL}/search.php", {"f": letter})
    return data.get('meals') or []


def get_all_meals() -> list:
    """获取所有菜品数据"""
    all_meals = []
    seen_ids = set()
    
    print("开始爬取 TheMealDB 数据...")
    print("=" * 50)
    
    for i, letter in enumerate(LETTERS):
        print(f"[{i+1}/{len(LETTERS)}] 爬取字母 {letter.upper()} ...", end=" ", flush=True)
        meals = get_meals_by_letter(letter)
        
        new_count = 0
        for meal in meals:
            if meal['idMeal'] not in seen_ids:
                seen_ids.add(meal['idMeal'])
                all_meals.append(meal)
                new_count += 1
        
        print(f"获取 {len(meals)} 条, 新增 {new_count} 条, 累计 {len(all_meals)} 条")
        time.sleep(0.3)
    
    print("=" * 50)
    print(f"总计获取 {len(all_meals)} 条菜品数据")
    return all_meals


def generate_ingredients(meal: dict) -> str:
    """提取食材列表"""
    ingredients = []
    for i in range(1, 21):
        ingredient = meal.get(f'strIngredient{i}')
        measure = meal.get(f'strMeasure{i}')
        if ingredient and ingredient.strip():
            if measure and measure.strip():
                ingredients.append(f"{measure.strip()} {ingredient.strip()}")
            else:
                ingredients.append(ingredient.strip())
    return ", ".join(ingredients)


def convert_to_product(meal: dict) -> dict:
    """转换为产品格式"""
    category_name = meal.get('strCategory', '')
    category_id = CATEGORY_MAPPING.get(category_name, 1)
    
    area = meal.get('strArea', '')
    ingredients = generate_ingredients(meal)
    instructions = meal.get('strInstructions', '')[:500]
    
    description = f"【{area}菜】{instructions}"
    if ingredients:
        description += f"\n\n主要食材：{ingredients[:200]}"
    
    norm_price = round(random.uniform(15.0, 99.0), 2)
    real_price = round(random.uniform(12.0, norm_price * 0.9), 2)
    sales = random.randint(0, 500)
    
    return {
        "fid": str(uuid.uuid4()),
        "category_id": category_id,
        "name": meal.get('strMeal', ''),
        "cover": meal.get('strMealThumb', ''),
        "description": description,
        "norm_price": norm_price,
        "real_price": real_price,
        "stock": 100,
        "sales": sales,
        "status": 0,
    }


def generate_category_sql() -> str:
    """生成分类表 SQL"""
    now = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    
    lines = [
        "-- 分类数据",
        "-- " + "=" * 60,
        f"-- 生成时间: {now}",
        "-- " + "=" * 60,
        "",
        "-- 创建测试数据库",
        "CREATE DATABASE IF NOT EXISTS delivery_test DEFAULT CHARACTER SET utf8mb4;",
        "USE delivery_test;",
        "",
        "-- 分类表",
        """CREATE TABLE IF NOT EXISTS t_category (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    sort INT DEFAULT 0,
    status INT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;""",
        "",
        "-- 清空并插入分类数据",
        "TRUNCATE TABLE t_category;",
        "",
        "INSERT INTO t_category (id, name, sort, status, create_time, update_time, is_deleted) VALUES",
    ]
    
    values = []
    for cat in ALL_CATEGORIES:
        values.append(
            f"  ({cat['id']}, '{cat['name']}', {cat['sort']}, {cat['status']}, '{now}', '{now}', 0)"
        )
    
    lines.append(",\n".join(values) + ";")
    return "\n".join(lines)


def generate_product_sql(products: list) -> str:
    """生成菜品表 SQL"""
    now = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    
    lines = [
        "-- 菜品数据",
        "-- " + "=" * 60,
        f"-- 生成时间: {now}",
        f"-- 数据条数: {len(products)}",
        "-- " + "=" * 60,
        "",
        "USE delivery_test;",
        "",
        "-- 菜品表",
        """CREATE TABLE IF NOT EXISTS t_product (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    fid VARCHAR(36) NOT NULL,
    category_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    cover VARCHAR(500),
    description TEXT,
    norm_price DECIMAL(10,2) DEFAULT 0.00,
    real_price DECIMAL(10,2) DEFAULT 0.00,
    stock INT DEFAULT 0,
    sales INT DEFAULT 0,
    status INT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted INT DEFAULT 0,
    INDEX idx_category_id (category_id),
    INDEX idx_name (name),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;""",
        "",
        "-- 清空并插入菜品数据",
        "TRUNCATE TABLE t_product;",
        "",
    ]
    
    # 分批插入，每批200条
    batch_size = 200
    for batch_start in range(0, len(products), batch_size):
        batch_end = min(batch_start + batch_size, len(products))
        batch = products[batch_start:batch_end]
        
        if batch_start > 0:
            lines.append("")
        
        lines.append(f"-- 第 {batch_start//batch_size + 1} 批 (共 {len(products)} 条)")
        lines.append("INSERT INTO t_product (fid, category_id, name, cover, description, norm_price, real_price, stock, sales, status, create_time, update_time, is_deleted) VALUES")
        
        values = []
        for p in batch:
            name = p['name'].replace("'", "''")
            description = p['description'].replace("'", "''")
            cover = p['cover'].replace("'", "''")
            
            values.append(
                f"  ('{p['fid']}', {p['category_id']}, '{name}', '{cover}', '{description}', "
                f"{p['norm_price']}, {p['real_price']}, {p['stock']}, {p['sales']}, "
                f"{p['status']}, '{now}', '{now}', 0)"
            )
        
        lines.append(",\n".join(values) + ";")
    
    return "\n".join(lines)


def save_raw_data(meals: list):
    """保存原始数据"""
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    filepath = os.path.join(OUTPUT_DIR, "meals_raw.json")
    with open(filepath, 'w', encoding='utf-8') as f:
        json.dump(meals, f, ensure_ascii=False, indent=2)
    print(f"[保存] 原始数据已保存到: {filepath}")


def main():
    print("=" * 60)
    print("  菜品数据爬虫 - TheMealDB")
    print("  目标: 获取 500 条菜品数据")
    print("  输出: SQL 脚本文件")
    print("=" * 60)
    print()
    
    # 1. 获取数据
    print("[步骤1] 爬取 TheMealDB 数据...")
    meals = get_all_meals()
    
    if not meals:
        print("[失败] 未能获取任何菜品数据")
        return
    
    save_raw_data(meals)
    print()
    
    # 2. 转换数据
    print("[步骤2] 转换数据格式...")
    products = [convert_to_product(meal) for meal in meals]
    print(f"[成功] 转换完成，共 {len(products)} 条数据")
    print()
    
    # 3. 生成 SQL
    print("[步骤3] 生成 SQL 脚本...")
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    
    category_sql = generate_category_sql()
    product_sql = generate_product_sql(products)
    
    # 保存 SQL 文件
    category_sql_path = os.path.join(OUTPUT_DIR, "insert_categories.sql")
    with open(category_sql_path, 'w', encoding='utf-8') as f:
        f.write(category_sql)
    print(f"[保存] {category_sql_path}")
    
    product_sql_path = os.path.join(OUTPUT_DIR, "insert_products.sql")
    with open(product_sql_path, 'w', encoding='utf-8') as f:
        f.write(product_sql)
    print(f"[保存] {product_sql_path}")
    
    # 4. 统计
    print()
    print("=" * 60)
    print("  执行完成!")
    print("=" * 60)
    print(f"  分类数据: {len(ALL_CATEGORIES)} 条")
    print(f"  菜品数据: {len(products)} 条")
    print()
    print("  使用方法:")
    print("  1. 确保 MySQL 服务已启动")
    print("  2. 登录 MySQL: mysql -u root -p")
    print(f"  3. 执行 SQL: source {category_sql_path}")
    print(f"  4. 执行 SQL: source {product_sql_path}")
    print("  或直接运行: mysql -u root -p < insert_products.sql")
    print()
    print("=" * 60)


if __name__ == "__main__":
    main()
