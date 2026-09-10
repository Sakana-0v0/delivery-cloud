# -*- coding: utf-8 -*-
"""
Reclassify historical products using LLM
"""
import argparse
import logging
import re
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from collections import Counter

import pymysql
import requests
import yaml

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%H:%M:%S"
)
logger = logging.getLogger("recategorize")


CATEGORY_LIST = """1. 招牌主菜 - 中餐重磅肉菜、家宴主菜
2. 家常小炒 - 中餐快炒、家常炒菜
3. 鲜汤靓煲 - 汤、煲类
4. 米面主食 - 米饭、面食、饼类主食
5. 西式主菜 - 牛排、烤鸡、意面等西餐主菜
6. 西式轻食 - 沙拉、三明治、汉堡
7. 甜品烘焙 - 蛋糕、布丁、饼干、烘焙
8. 风味小吃 - 小吃、配菜、早餐、杂项
9. 海鲜西餐 - 鱼、虾、贝类海鲜
10. 异国料理 - 泰国、印度、墨西哥、土耳其等异国菜
11. 茶饮果饮 - 茶、咖啡、果汁、奶昔"""

SYSTEM_PROMPT = f"""你是餐饮分类专家。根据菜品信息，从以下分类中选择最合适的分类编号。

{CATEGORY_LIST}

只返回一个数字（1-11），不要任何其他文字、解释或标点。"""


def load_config():
    with open("config.yaml", "r", encoding="utf-8") as f:
        return yaml.safe_load(f)


def call_llm(name, description, config):
    user_prompt = (
        f"菜品名：{name}\n"
        f"菜品描述：{(description or '')[:300]}\n\n"
        f"分类 ID："
    )

    response = requests.post(
        f"{config['llm']['base_url']}/chat/completions",
        headers={
            "Authorization": f"Bearer {config['llm']['api_key']}",
            "Content-Type": "application/json"
        },
        json={
            "model": config['llm'].get('model', 'qwen-plus'),
            "messages": [
                {"role": "system", "content": SYSTEM_PROMPT},
                {"role": "user", "content": user_prompt}
            ],
            "temperature": 0,
            "max_tokens": 5
        },
        timeout=30
    )
    response.raise_for_status()
    data = response.json()
    content = data["choices"][0]["message"]["content"].strip()

    match = re.search(r"\d+", content)
    if match:
        cat_id = int(match.group())
        if 1 <= cat_id <= 11:
            return cat_id
    return None


def load_products(conn):
    with conn.cursor() as cur:
        cur.execute(
            "SELECT id, name, description, category_id "
            "FROM t_product ORDER BY id"
        )
        return cur.fetchall()


def classify_one(product, config):
    pid, name, desc, old_cat = product
    try:
        new_cat = call_llm(name, desc, config)
        if new_cat and new_cat != old_cat:
            return (pid, old_cat, new_cat, name)
        return None
    except Exception as e:
        logger.warning(f"Failed id={pid} name={name}: {e}")
        return None


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--workers", type=int, default=5)
    parser.add_argument("--limit", type=int, default=0)
    args = parser.parse_args()

    config = load_config()
    if not config.get("llm", {}).get("enabled"):
        logger.error("llm.enabled must be true")
        return

    conn = pymysql.connect(
        host=config['database']['host'],
        port=config['database'].get('port', 3306),
        user=config['database']['user'],
        password=config['database']['password'],
        database=config['database']['database'],
        charset='utf8mb4'
    )

    products = load_products(conn)
    if args.limit > 0:
        products = products[:args.limit]
    logger.info(f"Loaded {len(products)} products")

    changes = []
    start = time.time()
    with ThreadPoolExecutor(max_workers=args.workers) as executor:
        futures = {
            executor.submit(classify_one, p, config): p
            for p in products
        }
        for i, future in enumerate(as_completed(futures), 1):
            result = future.result()
            if result:
                changes.append(result)
            if i % 50 == 0:
                logger.info(f"Progress: {i}/{len(products)}")

    elapsed = time.time() - start
    logger.info(f"Done: {elapsed:.1f}s, {len(changes)} to update")

    cat_counter = Counter((old, new) for _, old, new, _ in changes)
    logger.info("Changes (old -> new : count):")
    for (old, new), cnt in sorted(cat_counter.items()):
        logger.info(f"  {old:>2} -> {new:>2} : {cnt}")

    if args.dry_run:
        logger.info("[DRY RUN] Sample (20):")
        for pid, old, new, name in changes[:20]:
            logger.info(f"  ID {pid}: {old} -> {new} | {name}")
        return

    if not changes:
        logger.info("Nothing to update")
        return

    confirm = input(f"Update {len(changes)} records. Confirm? (yes/no): ")
    if confirm.lower() != "yes":
        logger.info("Cancelled")
        return

    with conn.cursor() as cur:
        for pid, old, new, _ in changes:
            cur.execute(
                "UPDATE t_product SET category_id = %s WHERE id = %s",
                (new, pid)
            )
        conn.commit()
    logger.info(f"OK Updated {len(changes)} products")


if __name__ == "__main__":
    main()