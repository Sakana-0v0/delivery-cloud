# -*- coding: utf-8 -*-
"""
Step 2: 生成数据字典模板
功能：读取 raw_dishes.jsonl，生成 dish_dictionary.json 供后续编辑
"""

import json
import argparse
from pathlib import Path
from datetime import datetime

DEFAULT_RAW_FILE = "raw_dishes.jsonl"
DEFAULT_DICT_FILE = "dish_dictionary.json"


def generate_dictionary(raw_file: str, output_file: str) -> int:
    """生成数据字典模板"""
    raw_path = Path(raw_file)
    if not raw_path.exists():
        print(f"[!] 错误: 找不到原始数据文件 {raw_file}")
        print(f"    请先运行 crawl_meal.py 生成数据")
        return 0
    
    print(f"[{datetime.now().strftime('%H:%M:%S')}] 读取原始数据: {raw_path.absolute()}")
    
    template = {}
    
    with open(raw_path, 'r', encoding='utf-8') as f:
        for line_num, line in enumerate(f, 1):
            try:
                meal = json.loads(line.strip())
                meal_id = meal['idMeal']
                
                template[meal_id] = {
                    "english_name": meal['strMeal'],
                    "chinese_name": "",           # 待翻译
                    "image_file": f"{meal_id}.jpg",
                    "category_id": 0,             # 待映射
                    "category_name": "",           # 待映射
                    "description": "",            # 待改写
                    "norm_price": 0.0,            # 待生成
                    "real_price": 0.0,            # 待计算
                    "stock": 999,
                    "sales": 0,                   # 待生成
                    "area": meal.get('strArea', ''),
                    "instructions_preview": meal['strInstructions'][:200],
                    # 原始数据保留
                    "_raw": {
                        "strCategory": meal.get('strCategory', ''),
                        "strMealThumb": meal.get('strMealThumb', ''),
                        "strInstructions": meal['strInstructions'],
                    }
                }
            except json.JSONDecodeError as e:
                print(f"  [!] 第 {line_num} 行 JSON 解析失败: {e}")
            except KeyError as e:
                print(f"  [!] 第 {line_num} 行缺少必要字段: {e}")
    
    # 写入数据字典
    output_path = Path(output_file)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    
    with open(output_path, 'w', encoding='utf-8') as f:
        json.dump(template, f, ensure_ascii=False, indent=2)
    
    print(f"[{datetime.now().strftime('%H:%M:%S')}] 完成！")
    print(f"[{datetime.now().strftime('%H:%M:%S')}] 生成模板: {len(template)} 条记录")
    print(f"[{datetime.now().strftime('%H:%M:%S')}] 输出文件: {output_path.absolute()}")
    print()
    print("数据字典字段说明:")
    print("  - chinese_name:  中文名称（需要翻译或手动填写）")
    print("  - category_id:    分类ID（需要映射到你系统的分类）")
    print("  - description:    商品描述（需要改写）")
    print("  - norm_price:     原价（需要填写）")
    print("  - real_price:     现价（需要填写，通常低于原价）")
    print("  - sales:          销量（需要填写）")
    print()
    print("提示: 你可以手动编辑 JSON 文件，或编写脚本批量处理。")
    
    return len(template)


def main():
    parser = argparse.ArgumentParser(description='生成数据字典模板')
    parser.add_argument('-i', '--input', default=DEFAULT_RAW_FILE,
                        help=f'原始数据文件 (默认: {DEFAULT_RAW_FILE})')
    parser.add_argument('-o', '--output', default=DEFAULT_DICT_FILE,
                        help=f'输出字典文件 (默认: {DEFAULT_DICT_FILE})')
    
    args = parser.parse_args()
    generate_dictionary(args.input, args.output)


if __name__ == '__main__':
    main()
