# -*- coding: utf-8 -*-
"""
Step 2.5: 批量填充数据字典
功能：
  1. 翻译英文菜名（使用内置翻译映射）
  2. 生成随机价格
  3. 生成随机销量
  4. 自动分配 category_id（使用正确的分类映射）
"""

import json
import random
import argparse
from pathlib import Path
from datetime import datetime

# 从 category_mapping 导入
from category_mapping import CATEGORY_MAPPING, CATEGORY_NAMES, NEW_CATEGORIES

# 简单翻译映射（TheMealDB 常见菜品中英对照）
SIMPLE_TRANSLATION = {
    "Chicken": "鸡",
    "Beef": "牛",
    "Pork": "猪",
    "Lamb": "羊",
    "Fish": "鱼",
    "Vegetarian": "素",
    "Pasta": "面",
    "Pizza": "披萨",
    "Soup": "汤",
    "Salad": "沙拉",
    "Dessert": "甜点",
    "Breakfast": "早餐",
    "Seafood": "海鲜",
    "Side": "小食",
    "Starter": "开胃",
    "Vegan": "纯素",
    "Miscellaneous": "其他",
    "Goat": "羊",
}

# 翻译映射（英文菜名关键词 -> 中文）
MEAL_TRANSLATIONS = {
    "Arrabiata": "阿拉伯塔番茄意面",
    "BBQ": "烧烤",
    "Biryani": "印度香饭",
    "Bolognese": "博洛尼亚肉酱",
    "Bread": "面包",
    "Budino": "布丁",
    "Burger": "汉堡",
    "Cakes": "蛋糕",
    "Calzone": "卡尔佐内披萨",
    "Carrot": "胡萝卜",
    "Cheese": "芝士",
    "Chicken": "鸡肉",
    "Chilli": "辣椒",
    "Chowder": "蛤蜊浓汤",
    "Cobbler": "果馅脆饼",
    "Couscous": "库斯库斯",
    "Curry": "咖喱",
    "Dal": "印度扁豆糊",
    "Dessert": "甜点",
    "Dumpling": "饺子",
    "Egg": "鸡蛋",
    "Fajitas": "法士达",
    "Fish": "鱼",
    "Fried": "炸",
    "Garlic": "蒜香",
    "Ginger": "姜汁",
    "Gratin": "焗烤",
    "Green": "绿",
    "Hamburger": "汉堡",
    "Hot": "辣",
    "Ice": "冰",
    "Jelly": "果冻",
    "Kebab": "烤肉串",
    "Kedgeree": "印度鱼蛋饭",
    "KFC": "肯德基风味",
    "Lamb": "羊肉",
    "Lasagna": "千层面",
    "Leg": "腿",
    "Linguine": "细长意面",
    "Liver": "肝",
    "Macaroni": "通心粉",
    "Meatballs": "肉丸",
    "Mince": "肉末",
    "Mousse": "慕斯",
    "Mushroom": "蘑菇",
    "Noodles": "面条",
    "Omelette": "煎蛋卷",
    "Onion": "洋葱",
    "Paella": "西班牙海鲜饭",
    "Pancake": "煎饼",
    "Pasta": "意面",
    "Pastel": "蛋挞",
    "Pie": "派",
    "Pirogi": "波兰饺子",
    "Pizza": "披萨",
    "Plaice": "鲽鱼",
    "Poke": "夏威夷刺身饭",
    "Polenta": "玉米粥",
    "Pork": "猪肉",
    "Potato": "土豆",
    "Prawn": "大虾",
    "Pudding": "布丁",
    "Ramen": "拉面",
    "Ravioli": "意大利饺子",
    "Ribs": "肋排",
    "Rice": "米饭",
    "Risotto": "意大利烩饭",
    "Roast": "烤",
    "Rolls": "面包卷",
    "Salad": "沙拉",
    "Salmon": "三文鱼",
    "Sandwich": "三明治",
    "Sauce": "酱汁",
    "Sausage": "香肠",
    "Scallop": "扇贝",
    "Shrimp": "虾",
    "Soup": "汤",
    "Spaghetti": "意大利面",
    "Steak": "牛排",
    "Stew": "炖菜",
    "Stir": "炒",
    "Strawberry": "草莓",
    "Sushi": "寿司",
    "Tacos": "塔可",
    "Teriyaki": "照烧",
    "Tiramisu": "提拉米苏",
    "Toast": "吐司",
    "Tofu": "豆腐",
    "Tom": "汤姆",
    "Tuna": "金枪鱼",
    "Turkey": "火鸡",
    "Tuscan": "托斯卡纳",
    "Vegetable": "蔬菜",
    "Veggie": "素食",
    "Waffle": "华夫饼",
    "Winter": "冬季",
    "With": "配",
    "Yorkshire": "约克郡",
}

DEFAULT_INPUT_FILE = "dish_dictionary.json"
DEFAULT_OUTPUT_FILE = "dish_dictionary.json"


def translate_name(english_name: str, category: str) -> str:
    """翻译菜名"""
    # 先检查是否有完整匹配
    for eng, chn in MEAL_TRANSLATIONS.items():
        if eng.lower() in english_name.lower():
            return english_name.replace(eng, chn)
    
    # 否则用类别前缀
    category_prefix = SIMPLE_TRANSLATION.get(category, "")
    if category_prefix:
        return f"{category_prefix}{english_name[:8]}"
    
    return english_name


def generate_description(name: str, area: str, instructions: str, category: str) -> str:
    """生成描述"""
    area_desc = {
        "Japanese": "日式",
        "Italian": "意式",
        "Chinese": "中式",
        "Mexican": "墨西哥",
        "Indian": "印度",
        "French": "法式",
        "Thai": "泰式",
        "American": "美式",
        "British": "英式",
        "Korean": "韩式",
        "Vietnamese": "越南",
        "Spanish": "西班牙",
        "Greek": "希腊",
        "German": "德式",
        "Dutch": "荷兰",
        "Croatian": "克罗地亚",
        "Polish": "波兰",
        "Portuguese": "葡萄牙",
        "Russian": "俄式",
        "Irish": "爱尔兰",
        "Filipino": "菲律宾",
        "Malaysian": "马来西亚",
        "Indonesian": "印尼",
        "Jamaican": "牙买加",
        "Kenyan": "肯尼亚",
        "Egyptian": "埃及",
        "Moroccan": "摩洛哥",
        "Tunisian": "突尼斯",
        "Turkish": "土耳其",
        "Brazilian": "巴西",
        "Argentine": "阿根廷",
        "Canadian": "加拿大",
        "Australian": "澳式",
    }.get(area, "")
    
    # 截取做法的前100字作为描述
    desc = instructions[:100].replace("\r\n", " ").replace("\n", " ").strip()
    if desc:
        return f"{area_desc}风味{desc}..."
    return f"{area_desc}特色美食，选用新鲜食材精心制作。"


def batch_process(input_file: str, output_file: str, 
                  min_price: float = 15.0, max_price: float = 88.0,
                  seed: int = None) -> int:
    """批量处理数据字典"""
    
    if seed is not None:
        random.seed(seed)
    
    input_path = Path(input_file)
    if not input_path.exists():
        print(f"[!] 错误: 找不到数据字典文件 {input_file}")
        return 0
    
    print(f"[{datetime.now().strftime('%H:%M:%S')}] 读取数据字典: {input_path.absolute()}")
    
    with open(input_path, 'r', encoding='utf-8') as f:
        dictionary = json.load(f)
    
    print(f"[{datetime.now().strftime('%H:%M:%S')}] 开始批量处理: {len(dictionary)} 条记录")
    print(f"[{datetime.now().strftime('%H:%M:%S')}] 价格区间: {min_price} - {max_price} 元")
    print(f"[{datetime.now().strftime('%H:%M:%S')}] 分类映射规则:")
    for cat_name, cat_id in CATEGORY_MAPPING.items():
        cat_chinese = CATEGORY_NAMES.get(cat_id, "未知")
        print(f"    {cat_name} -> {cat_id}.{cat_chinese}")
    print("-" * 50)
    
    processed = 0
    
    for meal_id, info in dictionary.items():
        themealdb_category = info.get('_raw', {}).get('strCategory', '')
        area = info.get('area', '')
        english_name = info.get('english_name', '')
        instructions = info.get('_raw', {}).get('strInstructions', '')
        
        # 翻译中文名
        if not info.get('chinese_name'):
            info['chinese_name'] = translate_name(english_name, themealdb_category)
        
        # 分配分类ID（使用正确的映射）
        if not info.get('category_id') or info['category_id'] == 0:
            info['category_id'] = CATEGORY_MAPPING.get(themealdb_category, 2)  # 默认精品小炒
            info['category_name'] = CATEGORY_NAMES.get(info['category_id'], "精品小炒")
        
        # 生成价格
        if info.get('norm_price', 0) == 0:
            norm_price = round(random.uniform(min_price, max_price), 2)
            info['norm_price'] = norm_price
            info['real_price'] = round(norm_price * random.uniform(0.75, 0.90), 2)
        
        # 生成销量
        if not info.get('sales'):
            info['sales'] = random.randint(0, 500)
        
        # 生成描述
        if not info.get('description'):
            info['description'] = generate_description(
                info['chinese_name'], 
                area, 
                instructions,
                themealdb_category
            )
        
        processed += 1
        
        if processed % 50 == 0:
            print(f"[{datetime.now().strftime('%H:%M:%S')}] 已处理: {processed}/{len(dictionary)}")
    
    # 写入文件
    output_path = Path(output_file)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    
    with open(output_path, 'w', encoding='utf-8') as f:
        json.dump(dictionary, f, ensure_ascii=False, indent=2)
    
    print("-" * 50)
    print(f"[{datetime.now().strftime('%H:%M:%S')}] 处理完成！")
    print(f"[{datetime.now().strftime('%H:%M:%S')}] 已处理: {processed} 条记录")
    print(f"[{datetime.now().strftime('%H:%M:%S')}] 输出文件: {output_path.absolute()}")
    
    # 统计各分类数量
    cat_stats = {}
    for info in dictionary.values():
        cid = info.get('category_id', 0)
        cat_stats[cid] = cat_stats.get(cid, 0) + 1
    
    print(f"[{datetime.now().strftime('%H:%M:%S')}] 分类统计:")
    for cid, count in sorted(cat_stats.items()):
        cname = CATEGORY_NAMES.get(cid, "未知")
        print(f"    {cid}.{cname}: {count} 条")
    
    return processed


def main():
    parser = argparse.ArgumentParser(description='批量处理数据字典')
    parser.add_argument('-i', '--input', default=DEFAULT_INPUT_FILE,
                        help=f'数据字典文件 (默认: {DEFAULT_INPUT_FILE})')
    parser.add_argument('-o', '--output', default=DEFAULT_OUTPUT_FILE,
                        help=f'输出文件 (默认: {DEFAULT_INPUT_FILE}，覆盖原文件）')
    parser.add_argument('--min-price', type=float, default=15.0,
                        help='最低价格 (默认: 15.0)')
    parser.add_argument('--max-price', type=float, default=88.0,
                        help='最高价格 (默认: 88.0)')
    parser.add_argument('--seed', type=int, default=None,
                        help='随机种子（用于复现相同结果）')
    
    args = parser.parse_args()
    batch_process(
        args.input, 
        args.output,
        min_price=args.min_price,
        max_price=args.max_price,
        seed=args.seed
    )


if __name__ == '__main__':
    main()
