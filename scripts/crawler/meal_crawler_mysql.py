# -*- coding: utf-8 -*-
"""
菜品数据爬虫 - TheMealDB
直接从 MySQL 测试库插入数据
"""

import json
import random
import uuid
import requests
import time
import os
import mysql.connector
from datetime import datetime
from category_mapping import CATEGORY_MAPPING, ALL_CATEGORIES

# API 配置
BASE_URL = "https://www.themealdb.com/api/json/v1/1"
OUTPUT_DIR = "scripts/crawler/data"

# 数据库配置（测试库）
DB_CONFIG = {
    'host': 'localhost',
    'port': 3306,
    'user': 'root',
    'password': 'root',  # 请根据实际情况修改
    'database': 'delivery_test',
    'charset': 'utf8mb4'
}

# 字母列表
LETTERS = list('abcdefghijklmnopqrstuvwxyz')


def get_db_connection():
    """获取数据库连接"""
    try:
        conn = mysql.connector.connect(**DB_CONFIG)
        return conn
    except mysql.connector.Error as e:
        print(f"[错误] 数据库连接失败: {e}")
        print("请检查数据库配置或确保 MySQL 服务已启动")
        return None


def init_database():
    """初始化数据库表"""
    conn = get_db_connection()
    if not conn:
        return False
    
    cursor = conn.cursor()
    
    try:
        # 创建分类表
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS t_category (
                id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                name VARCHAR(50) NOT NULL,
                sort INT DEFAULT 0,
                status INT DEFAULT 0,
                create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                is_deleted INT DEFAULT 0
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """)
        
        # 创建菜品表
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS t_product (
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
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """)
        
        conn.commit()
        print("[成功] 数据库表初始化完成")
        return True
        
    except mysql.connector.Error as e:
        print(f"[错误] 初始化数据库失败: {e}")
        return False
    finally:
        cursor.close()
        conn.close()


def get_meals_by_letter(letter: str) -> list:
    """按首字母搜索菜品"""
    url = f"{BASE_URL}/search.php"
    params = {"f": letter}
    
    try:
        response = requests.get(url, params=params, timeout=30)
        response.raise_for_status()
        data = response.json()
        return data.get('meals') or []
    except Exception as e:
        print(f"  [错误] 字母 {letter}: {e}")
        return []


def get_all_meals() -> list:
    """获取所有菜品数据"""
    all_meals = []
    seen_ids = set()
    
    print("开始爬取 TheMealDB 数据...")
    print("=" * 50)
    
    for i, letter in enumerate(LETTERS):
        print(f"[{i+1}/{len(LETTERS)}] 爬取字母 {letter.upper()} ...", end=" ")
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


def insert_categories(cursor):
    """插入分类数据"""
    now = datetime.now()
    
    # 先清空分类表
    cursor.execute("TRUNCATE TABLE t_category")
    
    for cat in ALL_CATEGORIES:
        cursor.execute("""
            INSERT INTO t_category (id, name, sort, status, create_time, update_time, is_deleted)
            VALUES (%s, %s, %s, %s, %s, %s, %s)
        """, (cat['id'], cat['name'], cat['sort'], cat['status'], now, now, 0))
    
    print(f"[成功] 插入 {len(ALL_CATEGORIES)} 条分类数据")


def insert_products(cursor, products: list):
    """批量插入菜品数据"""
    # 先清空菜品表
    cursor.execute("TRUNCATE TABLE t_product")
    
    now = datetime.now()
    batch_size = 100
    
    for i in range(0, len(products), batch_size):
        batch = products[i:i+batch_size]
        values = []
        for p in batch:
            values.append((
                p['fid'], p['category_id'], p['name'], p['cover'], 
                p['description'], p['norm_price'], p['real_price'],
                p['stock'], p['sales'], p['status'], now, now, 0
            ))
        
        cursor.executemany("""
            INSERT INTO t_product 
            (fid, category_id, name, cover, description, norm_price, real_price, stock, sales, status, create_time, update_time, is_deleted)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        """, values)
        
        print(f"[进度] 已插入 {min(i+batch_size, len(products))}/{len(products)} 条菜品数据")
    
    print(f"[成功] 共插入 {len(products)} 条菜品数据")


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
    print("  目标库: delivery_test (测试库)")
    print("=" * 60)
    print()
    
    # 1. 初始化数据库
    print("[步骤1] 初始化测试数据库...")
    if not init_database():
        print("[失败] 数据库初始化失败，程序退出")
        return
    print()
    
    # 2. 爬取数据
    print("[步骤2] 爬取 TheMealDB 数据...")
    meals = get_all_meals()
    
    if not meals:
        print("[失败] 未能获取任何菜品数据")
        return
    
    save_raw_data(meals)
    print()
    
    # 3. 数据转换
    print("[步骤3] 转换数据格式...")
    products = [convert_to_product(meal) for meal in meals]
    print(f"[成功] 转换完成，共 {len(products)} 条数据")
    print()
    
    # 4. 插入数据库
    print("[步骤4] 插入数据库...")
    conn = get_db_connection()
    if not conn:
        print("[失败] 数据库连接失败")
        return
    
    cursor = conn.cursor()
    
    try:
        # 插入分类
        print("  - 插入分类数据...")
        insert_categories(cursor)
        
        # 插入菜品
        print("  - 插入菜品数据...")
        insert_products(cursor, products)
        
        conn.commit()
        
        # 5. 验证
        print()
        print("[步骤5] 验证数据...")
        cursor.execute("SELECT COUNT(*) FROM t_category")
        cat_count = cursor.fetchone()[0]
        cursor.execute("SELECT COUNT(*) FROM t_product")
        prod_count = cursor.fetchone()[0]
        
        print(f"  - 分类表: {cat_count} 条")
        print(f"  - 菜品表: {prod_count} 条")
        
    except mysql.connector.Error as e:
        print(f"[错误] 数据库操作失败: {e}")
        conn.rollback()
    finally:
        cursor.close()
        conn.close()
    
    print()
    print("=" * 60)
    print("  执行完成!")
    print("=" * 60)


if __name__ == "__main__":
    main()
