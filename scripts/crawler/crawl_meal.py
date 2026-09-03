# -*- coding: utf-8 -*-
"""
Step 1: 爬取 TheMealDB 全量菜品原始数据
功能：按字母 A-Z 遍历 API，保存原始 JSONL 数据
"""

import requests
import json
import time
import argparse
from pathlib import Path
from datetime import datetime

# API 配置
API_BASE = "https://www.themealdb.com/api/json/v1/1"
DEFAULT_OUTPUT = "raw_dishes.jsonl"
REQUEST_INTERVAL = 0.25  # 秒，防止频率过高


def crawl_letter(letter: str, timeout: int = 10) -> list:
    """爬取单个字母的菜品"""
    url = f"{API_BASE}/search.php?f={letter}"
    try:
        resp = requests.get(url, timeout=timeout)
        resp.raise_for_status()
        data = resp.json()
        meals = data.get('meals') or []
        
        # 提取关键字段
        result = []
        for meal in meals:
            result.append({
                'idMeal': meal.get('idMeal', ''),
                'strMeal': meal.get('strMeal', ''),
                'strCategory': meal.get('strCategory', ''),
                'strArea': meal.get('strArea', ''),
                'strMealThumb': meal.get('strMealThumb', ''),
                'strInstructions': (meal.get('strInstructions') or '')[:500],  # 截取前500字
            })
        return result
    except requests.RequestException as e:
        print(f"  [!] 请求失败 [{letter}]: {e}")
        return []


def crawl_all_meals(output_file: str, show_detail: bool = False) -> int:
    """爬取全量菜品"""
    print(f"[{datetime.now().strftime('%H:%M:%S')}] 开始爬取 TheMealDB...")
    print(f"[{datetime.now().strftime('%H:%M:%S')}] 输出文件: {output_file}")
    print("-" * 50)
    
    all_meals = []
    letters = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'
    
    for i, letter in enumerate(letters):
        print(f"[{datetime.now().strftime('%H:%M:%S')}] [{i+1}/26] 正在爬取字母 {letter}...", end=' ')
        
        meals = crawl_letter(letter)
        count = len(meals)
        all_meals.extend(meals)
        
        if show_detail and count > 0:
            print(f"获取 {count} 道菜品")
            for meal in meals[:3]:  # 只显示前3道
                print(f"    - {meal['strMeal']} ({meal['strArea']})")
            if count > 3:
                print(f"    ... 还有 {count - 3} 道")
        else:
            print(f"获取 {count} 道菜品")
        
        time.sleep(REQUEST_INTERVAL)
    
    # 去重（按 idMeal）
    unique = {m['idMeal']: m for m in all_meals}.values()
    
    # 写入 JSONL
    output_path = Path(output_file)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    
    with open(output_path, 'w', encoding='utf-8') as f:
        for meal in unique:
            f.write(json.dumps(meal, ensure_ascii=False) + '\n')
    
    print("-" * 50)
    print(f"[{datetime.now().strftime('%H:%M:%S')}] 完成！")
    print(f"[{datetime.now().strftime('%H:%M:%S')}] 总计获取: {len(all_meals)} 道菜品")
    print(f"[{datetime.now().strftime('%H:%M:%S')}] 去重后: {len(unique)} 道菜品")
    print(f"[{datetime.now().strftime('%H:%M:%S')}] 输出文件: {output_path.absolute()}")
    
    return len(unique)


def main():
    parser = argparse.ArgumentParser(description='爬取 TheMealDB 全量菜品数据')
    parser.add_argument('-o', '--output', default=DEFAULT_OUTPUT,
                        help=f'输出文件路径 (默认: {DEFAULT_OUTPUT})')
    parser.add_argument('-v', '--verbose', action='store_true',
                        help='显示详细信息')
    parser.add_argument('-i', '--interval', type=float, default=REQUEST_INTERVAL,
                        help=f'请求间隔秒数 (默认: {REQUEST_INTERVAL})')
    
    args = parser.parse_args()
    
    # 更新全局请求间隔
    global REQUEST_INTERVAL
    REQUEST_INTERVAL = args.interval
    
    crawl_all_meals(args.output, args.verbose)


if __name__ == '__main__':
    main()
