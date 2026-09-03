# -*- coding: utf-8 -*-
"""
Step 5: 生成入库 SQL
功能：读取 dish_dictionary_final.json，生成可执行的 INSERT SQL
"""

import json
import uuid
import argparse
from pathlib import Path
from datetime import datetime

DEFAULT_INPUT_FILE = "dish_dictionary_final.json"
DEFAULT_OUTPUT_FILE = "products_import.sql"


def escape_sql_string(s: str) -> str:
    """SQL 字符串转义（防止注入）"""
    if not s:
        return ""
    # 转义单引号
    return s.replace("'", "''")


def generate_sql(input_file: str, output_file: str) -> int:
    """生成入库 SQL"""
    input_path = Path(input_file)
    if not input_path.exists():
        print(f"[!] 错误: 找不到数据文件 {input_file}")
        print(f"    请先运行 download_and_upload.py 生成数据")
        return 0
    
    print(f"[{datetime.now().strftime('%H:%M:%S')}] 读取数据文件: {input_path.absolute()}")
    
    with open(input_path, 'r', encoding='utf-8') as f:
        data = json.load(f)
    
    if not data:
        print(f"[!] 数据为空，无记录可生成")
        return 0
    
    # 构建 SQL
    now = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    
    sql_lines = [
        "-- ===========================================",
        f"-- 菜品数据导入 SQL",
        f"-- 生成时间: {now}",
        f"-- 总记录数: {len(data)}",
        "-- ===========================================",
        "",
        "SET NAMES utf8mb4;",
        "SET FOREIGN_KEY_CHECKS = 0;",
        "",
        "-- 清理可能存在的旧数据（可选，取消注释即可启用）",
        "-- TRUNCATE TABLE t_product;",
        "",
        "INSERT INTO t_product (fid, category_id, name, cover, description, norm_price, real_price, stock, sales, status, create_time, update_time)",
        "VALUES"
    ]
    
    values = []
    for meal_id, info in data.items():
        # 生成 UUID 作为 fid
        fid = str(uuid.uuid4())
        
        # 获取字段值
        name = escape_sql_string(info.get('chinese_name') or info.get('english_name', ''))
        cover = escape_sql_string(info.get('minio_url', ''))
        description = escape_sql_string(info.get('description', ''))[:200]  # 限制描述长度
        category_id = info.get('category_id', 1)
        
        # 价格处理
        norm_price = float(info.get('norm_price', 0) or 0)
        real_price = float(info.get('real_price', 0) or 0)
        
        # 如果没有填写价格，生成一个默认价格
        if norm_price == 0:
            # 基于 area 生成不同价格区间
            area = info.get('area', '').lower()
            if area in ['japanese', 'korean', 'chinese']:
                norm_price = round(25.0 + (hash(meal_id) % 40), 2)  # 25-65 元
            elif area in ['italian', 'french', 'spanish', 'greek']:
                norm_price = round(35.0 + (hash(meal_id) % 50), 2)  # 35-85 元
            else:
                norm_price = round(20.0 + (hash(meal_id) % 50), 2)  # 20-70 元
        
        if real_price == 0:
            real_price = round(norm_price * (0.75 + (hash(meal_id + "price") % 15) / 100), 2)  # 75-90 折
        
        # 库存和销量
        stock = int(info.get('stock', 999) or 999)
        sales = int(info.get('sales', 0) or (hash(meal_id + "sales") % 500))  # 随机销量 0-500
        
        # 状态（0=上架）
        status = int(info.get('status', 0) or 0)
        
        # 构建 VALUES 子句
        value = (
            f"  ('{fid}', {category_id}, '{name}', '{cover}', '{description}', "
            f"{norm_price}, {real_price}, {stock}, {sales}, {status}, "
            f"'{now}', '{now}')"
        )
        values.append(value)
    
    # 连接所有 VALUES
    sql_lines.append(",\n".join(values) + ";")
    sql_lines.append("")
    sql_lines.append("SET FOREIGN_KEY_CHECKS = 1;")
    sql_lines.append(f"-- 导入完成: {len(values)} 条记录")
    
    # 写入文件
    output_path = Path(output_file)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    
    with open(output_path, 'w', encoding='utf-8') as f:
        f.write("\n".join(sql_lines))
    
    print(f"[{datetime.now().strftime('%H:%M:%S')}] 完成！")
    print(f"[{datetime.now().strftime('%H:%M:%S')}] 生成 SQL: {len(values)} 条记录")
    print(f"[{datetime.now().strftime('%H:%M:%S')}] 输出文件: {output_path.absolute()}")
    print()
    print("使用方法:")
    print(f"  mysql -u root -p your_password your_database < {output_path.name}")
    
    return len(values)


def main():
    parser = argparse.ArgumentParser(description='生成菜品入库 SQL')
    parser.add_argument('-i', '--input', default=DEFAULT_INPUT_FILE,
                        help=f'数据文件 (默认: {DEFAULT_INPUT_FILE})')
    parser.add_argument('-o', '--output', default=DEFAULT_OUTPUT_FILE,
                        help=f'输出 SQL 文件 (默认: {DEFAULT_OUTPUT_FILE})')
    
    args = parser.parse_args()
    generate_sql(args.input, args.output)


if __name__ == '__main__':
    main()
