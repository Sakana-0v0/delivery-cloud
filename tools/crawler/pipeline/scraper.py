# -*- coding: utf-8 -*-
"""
ScraperProcessor — 从 TheMealDB 爬取菜品数据
"""
import logging
import time
import random
from typing import List
import requests

from .base import Processor, ProductItem

logger = logging.getLogger(__name__)


class MealDBClient:
    """TheMealDB API 客户端"""

    BASE_URL = "https://www.themealdb.com/api/json/v1/1"

    # 11 个分类名称映射（与数据库 t_category 对齐）
    CATEGORY_NAMES = {
        1:  "招牌主菜",
        2:  "家常小炒",
        3:  "鲜汤靓煲",
        4:  "米面主食",
        5:  "西式主菜",
        6:  "西式轻食",
        7:  "甜品烘焙",
        8:  "风味小吃",
        9:  "海鲜西餐",
        10: "异国料理",
        11: "茶饮果饮",
    }

    # 异国料理国家集合
    FOREIGN_AREAS = {
        "thai", "vietnamese", "malaysian", "indian", "mexican",
        "turkish", "moroccan", "russian", "filipino", "kenyan",
        "egyptian", "brazilian", "argentine", "peruvian", "cuban",
        "jamaican", "tunisian", "lebanese", "pakistani", "iranian",
        "algerian", "libyan", "sudanese", "ethiopian", "nigerian", "kenyan",
    }

    # 价格区间配置（按 area）
    PRICE_RANGES = {
        "Japanese": (35, 68), "Italian": (38, 78), "Chinese": (25, 55),
        "Mexican": (30, 60), "Indian": (28, 58), "French": (40, 85),
        "Thai": (30, 65), "American": (32, 62), "British": (30, 60),
        "Korean": (32, 65), "Spanish": (35, 72), "Greek": (35, 70),
        "Vietnamese": (25, 50), "Other": (20, 55),
    }

    def __init__(self):
        self.session = requests.Session()
        self.session.headers.update({
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
        })

    def resolve_category_id(self, category: str, area: str) -> int:
        """根据 TheMealDB 的 category + area 解析到数据库 category_id
        
        分类逻辑（11 类）：
        - 招牌主菜 (1): 中餐 + 肉菜主料
        - 家常小炒 (2): 默认兜底
        - 鲜汤靓煲 (3): soup
        - 米面主食 (4): pasta
        - 西式主菜 (5): 肉菜主料（默认）
        - 西式轻食 (6): vegan/vegetarian
        - 甜品烘焙 (7): dessert
        - 风味小吃 (8): breakfast/side/starter/miscellaneous
        - 海鲜西餐 (9): seafood/fish
        - 异国料理 (10): 肉菜主料 + 异国 area
        - 茶饮果饮 (11): 当前未使用（预留）
        """
        cat = (category or "").strip().lower()
        area = (area or "").strip().lower()

        # 汤品
        if cat == "soup":
            return 3

        # 主食（意面）
        if cat == "pasta":
            return 4

        # 海鲜
        if cat in ("seafood", "fish"):
            return 9

        # 甜品
        if cat == "dessert":
            return 7

        # 小吃/早餐/配菜/杂项
        if cat in ("breakfast", "side", "starter", "miscellaneous"):
            return 8

        # 素食
        if cat in ("vegan", "vegetarian"):
            return 6

        # 肉菜主料
        if cat in ("beef", "chicken", "pork", "lamb", "goat"):
            if area == "chinese":
                return 1   # 招牌主菜（中餐重磅）
            if area in self.FOREIGN_AREAS:
                return 10  # 异国料理
            return 5       # 西式主菜（默认）

        # 默认兜底
        return 2

    def search_by_first_letter(self, letter: str) -> list:
        """按首字母搜索"""
        url = f"{self.BASE_URL}/search.php?f={letter}"
        try:
            resp = self.session.get(url, timeout=10)
            resp.raise_for_status()
            data = resp.json()
            return data.get('meals') or []
        except requests.RequestException as e:
            logger.warning(f"请求字母 {letter} 失败: {e}")
            return []

    def get_meal_detail(self, meal_id: str) -> dict:
        """获取菜品详情"""
        url = f"{self.BASE_URL}/lookup.php?i={meal_id}"
        try:
            resp = self.session.get(url, timeout=10)
            resp.raise_for_status()
            data = resp.json()
            meals = data.get('meals') or []
            return meals[0] if meals else {}
        except requests.RequestException as e:
            logger.warning(f"请求详情 {meal_id} 失败: {e}")
            return {}

    def crawl_all(self, limit: int = 300) -> list:
        """爬取全量数据"""
        all_meals = {}

        for letter in 'ABCDEFGHIJKLMNOPQRSTUVWXYZ':
            logger.info(f"爬取字母 {letter}...")
            meals = self.search_by_first_letter(letter)

            for meal in meals:
                meal_id = meal.get('idMeal')
                if meal_id and meal_id not in all_meals:
                    detail = self.get_meal_detail(meal_id)
                    if detail:
                        all_meals[meal_id] = detail
                    else:
                        all_meals[meal_id] = meal

            time.sleep(0.25)  # 防封

            if len(all_meals) >= limit:
                break

        return list(all_meals.values())[:limit]


class ScraperProcessor(Processor):
    """从 TheMealDB 爬取数据的 Processor"""

    def __init__(self, config: dict = None):
        super().__init__(config)
        self.client = MealDBClient()
        self.limit = config.get('limit', 50) if config else 50

    def process(self, items: List[ProductItem]) -> List[ProductItem]:
        """爬取数据并转换为 ProductItem"""
        logger.info(f"[开始] 爬取 TheMealDB (limit={self.limit})")

        meals = self.client.crawl_all(limit=self.limit)

        result = []
        for meal in meals:
            item = self._transform_meal(meal)
            result.append(item)

        logger.info(f"[完成] 爬取到 {len(result)} 条数据")
        return result

    def _transform_meal(self, meal: dict) -> ProductItem:
        """将 TheMealDB 数据转换为 ProductItem"""
        category = meal.get('strCategory', 'Miscellaneous')
        area = meal.get('strArea', 'Other')

        # 获取价格区间
        price_range = self.client.PRICE_RANGES.get(area, self.client.PRICE_RANGES["Other"])
        norm_price = round(random.uniform(*price_range), 2)
        real_price = round(norm_price * random.uniform(0.75, 0.90), 2)

        # 截取描述
        instructions = meal.get('strInstructions', '')[:200]

        # 解析分类 ID（11 类映射）
        category_id = self.client.resolve_category_id(category, area)

        return ProductItem(
            source_id=meal.get('idMeal', ''),
            name=meal.get('strMeal', ''),
            description=instructions,
            category=category,
            area=area,
            cover_url=meal.get('strMealThumb', ''),
            category_id=category_id,
            category_name=self.client.CATEGORY_NAMES.get(category_id, '家常小炒'),
            norm_price=norm_price,
            real_price=real_price,
            stock=random.randint(100, 999),
            sales=random.randint(0, 500),
            status=0,
        )