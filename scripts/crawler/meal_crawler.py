# -*- coding: utf-8 -*-
"""
菜品数据爬虫 - TheMealDB
从 TheMealDB API 爬取菜品数据，生成 SQL 插入脚本
"""

import json
import random
import uuid
import requests
import time
from datetime import datetime
from category_mapping import CATEGORY_MAPPING, ALL_CATEGORIES

# API 配置
BASE_URL = "https://www.themealdb.com/api/json/v1/1"
OUTPUT_DIR = "scripts/crawler/data"

# 字母列表（用于按首字母遍历）
LETTERS = list('abcdefghijklmnopqrstuvwxyz')


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
        
        # 去重（某些菜品可能在多个字母下出现）
        new_count = 0
        for meal in meals:
            if meal['idMeal'] not in seen_ids:
                seen_ids.add(meal['idMeal'])
                all_meals.append(meal)
                new_count += 1
        
        print(f"获取 {len(meals)} 条, 新增 {new_count} 条, 累计 {len(all_meals)} 条")
        time.sleep(0.3)  # 避免请求过快
    
    print("=" * 50)
    print(f"总计获取 {len(all_meals)} 条菜品数据")
    return all_meals


def generate_ingredients(meal: dict) -> str:
    """从菜品数据中提取食材列表"""
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


def convert_to_product(meal: dict, categories: list) -> dict:
    """将 TheMealDB 菜品转换为 t_product 格式"""
    category_name = meal.get('strCategory', '')
    category_id = CATEGORY_MAPPING.get(category_name, 1)
    
    # 获取分类名称
    category_obj = next((c for c in categories if c['id'] == category_id), categories[0])
    
    # 生成描述（地区 + 食材）
    area = meal.get('strArea', '')
    ingredients = generate_ingredients(meal)
    instructions = meal.get('strInstructions', '')[:500]  # 截取前500字符
    
    description = f"【{area}菜】{instructions}"
    if ingredients:
        description += f"\n\n主要食材：{ingredients[:200]}"
    
    # 随机生成价格和销量
    norm_price = round(random.uniform(15.0, 99.0), 2)
    real_price = round(random.uniform(12.0, norm_price * 0.9), 2)
    sales = random.randint(0, 500)
    
    return {
        "fid": str(uuid.uuid4()),
        "categoryId": category_id,
        "name": meal.get('strMeal', ''),
        "cover": meal.get('strMealThumb', ''),
        "description": description,
        "normPrice": norm_price,
        "realPrice": real_price,
        "stock": 100,
        "sales": sales,
        "status": 0,
    }


def generate_category_sql() -> str:
    """生成分类表 SQL"""
    now = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    
    sql_lines = ["-- 分类数据", "-- " + "=" * 60]
    sql_lines.append(f"-- 生成时间: {now}\n")
    sql_lines.append("-- " + "=" * 60)
    sql_lines.append("")
    sql_lines.append("-- 先清空分类表（可选）")
    sql_lines.append("-- TRUNCATE TABLE t_category;")
    sql_lines.append("")
    sql_lines.append("INSERT INTO t_category (id, name, sort, status, create_time, update_time, is_deleted) VALUES")
    
    values = []
    for cat in ALL_CATEGORIES:
        values.append(
            f"  ({cat['id']}, '{cat['name']}', {cat['sort']}, {cat['status']}, '{now}', '{now}', 0)"
        )
    
    sql_lines.append(",\n".join(values) + ";")
    return "\n".join(sql_lines)


def generate_product_sql(products: list) -> str:
    """生成菜品表 SQL"""
    now = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    
    sql_lines = ["-- 菜品数据"]
    sql_lines.append("-- " + "=" * 60)
    sql_lines.append(f"-- 生成时间: {now}")
    sql_lines.append(f"-- 数据条数: {len(products)}")
    sql_lines.append("-- " + "=" * 60)
    sql_lines.append("")
    sql_lines.append("-- 先清空菜品表（可选）")
    sql_lines.append("-- TRUNCATE TABLE t_product;")
    sql_lines.append("")
    sql_lines.append("INSERT INTO t_product (fid, category_id, name, cover, description, norm_price, real_price, stock, sales, status, create_time, update_time, is_deleted) VALUES")
    
    values = []
    for i, p in enumerate(products):
        # 转义单引号
        name = p['name'].replace("'", "''")
        description = p['description'].replace("'", "''")
        cover = p['cover'].replace("'", "''")
        
        values.append(
            f"  ('{p['fid']}', {p['categoryId']}, '{name}', '{cover}', '{description}', "
            f"{p['normPrice']}, {p['realPrice']}, {p['stock']}, {p['sales']}, "
            f"{p['status']}, '{now}', '{now}', 0)"
        )
        
        # 每1000条分一个INSERT块，避免SQL过长
        if (i + 1) % 1000 == 0:
            sql_lines.append(",\n".join(values) + ";")
            values = []
            if i + 1 < len(products):
                sql_lines.append("")
                sql_lines.append("INSERT INTO t_product (fid, category_id, name, cover, description, norm_price, real_price, stock, sales, status, create_time, update_time, is_deleted) VALUES")
    
    if values:
        sql_lines.append(",\n".join(values) + ";")
    
    return "\n".join(sql_lines)


def save_raw_data(meals: list):
    """保存原始数据到 JSON 文件"""
    import os
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    
    filepath = os.path.join(OUTPUT_DIR, "meals_raw.json")
    with open(filepath, 'w', encoding='utf-8') as f:
        json.dump(meals, f, ensure_ascii=False, indent=2)
    
    print(f"\n原始数据已保存到: {filepath}")


def main():
    print("=" * 60)
    print("  菜品数据爬虫 - TheMealDB")
    print("  目标: 获取 500 条菜品数据")
    print("=" * 60)
    print()
    
    # 1. 获取所有菜品数据
    meals = get_all_meals()
    
    if not meals:
        print("错误: 未能获取任何菜品数据")
        return
    
    # 2. 保存原始数据
    save_raw_data(meals)
    
    # 3. 转换为产品格式
    print("\n转换数据格式...")
    products = [convert_to_product(meal, ALL_CATEGORIES) for meal in meals]
    
    # 4. 生成 SQL
    print("生成 SQL 脚本...")
    category_sql = generate_category_sql()
    product_sql = generate_product_sql(products)
    
    # 5. 保存 SQL 文件
    import os
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    
    # 保存分类 SQL
    category_sql_path = os.path.join(OUTPUT_DIR, "insert_categories.sql")
    with open(category_sql_path, 'w', encoding='utf-8') as f:
        f.write(category_sql)
    print(f"分类 SQL 已保存到: {category_sql_path}")
    
    # 保存菜品 SQL
    product_sql_path = os.path.join(OUTPUT_DIR, "insert_products.sql")
    with open(product_sql_path, 'w', encoding='utf-8') as f:
        f.write(product_sql)
    print(f"菜品 SQL 已保存到: {product_sql_path}")
    
    # 6. 统计信息
    print()
    print("=" * 60)
    print("  执行完成!")
    print("=" * 60)
    print(f"  分类数据: {len(ALL_CATEGORIES)} 条")
    print(f"  菜品数据: {len(products)} 条")
    print()
    print("  使用方法:")
    print("  1. 先执行 insert_categories.sql 创建分类")
    print("  2. 再执行 insert_products.sql 创建菜品")
    print()
    print("=" * 60)


if __name__ == "__main__":
    main()
