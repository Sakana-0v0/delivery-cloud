# -*- coding: utf-8 -*-
"""
分类映射配置
TheMealDB 分类 -> t_category id

数据库现有分类（ID 1-4，不要重复创建）:
  1: 招牌主菜
  2: 精品小炒
  3: 汤品
  4: 主食

新增西式分类（ID 5-7）:
  5: 西式主菜
  6: 西式轻食
  7: 甜点饮品
"""

# TheMealDB 分类 -> 数据库 category_id 映射
CATEGORY_MAPPING = {
    "Beef": 5,            # 西式主菜
    "Chicken": 5,         # 西式主菜
    "Goat": 5,            # 西式主菜
    "Lamb": 5,            # 西式主菜
    "Pork": 5,            # 西式主菜
    "Fish": 5,            # 西式主菜

    "Breakfast": 2,       # 精品小炒
    "Miscellaneous": 2,    # 精品小炒

    "Soup": 3,            # 汤品

    "Pasta": 6,           # 西式轻食
    "Seafood": 6,         # 西式轻食
    "Vegetarian": 6,      # 西式轻食
    "Vegan": 6,           # 西式轻食

    "Dessert": 7,          # 甜点饮品
    "Side": 7,            # 甜点饮品
    "Starter": 7,         # 甜点饮品
}

# category_id -> 分类名称（完整映射）
CATEGORY_NAMES = {
    1: "招牌主菜",
    2: "精品小炒",
    3: "汤品",
    4: "主食",
    5: "西式主菜",
    6: "西式轻食",
    7: "甜点饮品",
}

# 新增分类列表（用于添加到数据库）
# 注意：ID 1-4 已存在，不要重复插入
NEW_CATEGORIES = [
    {"id": 5, "name": "西式主菜", "sort": 5, "status": 0},
    {"id": 6, "name": "西式轻食", "sort": 6, "status": 0},
    {"id": 7, "name": "甜点饮品", "sort": 7, "status": 0},
]


def get_category_id(themealdb_category: str) -> int:
    """根据 TheMealDB 分类名获取数据库 category_id"""
    return CATEGORY_MAPPING.get(themealdb_category, 2)  # 默认精品小炒


def get_category_name(category_id: int) -> str:
    """根据 category_id 获取分类名称"""
    return CATEGORY_NAMES.get(category_id, "未知分类")
