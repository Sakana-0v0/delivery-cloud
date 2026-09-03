# 菜品数据爬取工具

基于 TheMealDB API 爬取菜品数据，上传到 MinIO，并生成入库 SQL。

## 📁 脚本清单

| 脚本 | 功能 |
|------|------|
| `crawl_meal.py` | Step 1: 爬取 TheMealDB 全量菜品原始数据 |
| `generate_dictionary.py` | Step 2: 生成数据字典模板 |
| `batch_process.py` | Step 2.5: 批量翻译 + 生成价格 + 分配分类 |
| `download_and_upload.py` | Step 3: 下载图片并上传到 MinIO |
| `generate_sql.py` | Step 4: 生成入库 SQL |
| `run_all.py` | 一键运行所有步骤 |

## 📁 其他文件

| 文件 | 功能 |
|------|------|
| `category_mapping.py` | 分类映射配置（TheMealDB -> 数据库分类ID） |
| `add_western_categories.sql` | 添加西式分类的 SQL（执行一次即可） |
| `init_test_db.sql` | 旧版测试数据库脚本（已废弃） |

---

## 🚀 快速开始

### 1. 安装依赖

```bash
cd E:\Idea_project\delivery-cloud\scripts\crawler
pip install -r requirements.txt
```

### 2. 添加西式分类（仅需执行一次）

```bash
mysql -u root -p del_product_db < add_western_categories.sql
```

这会在数据库中添加 3 个新分类：

| ID | 分类名 | TheMealDB 对应分类 |
|----|--------|-------------------|
| 5 | 西式主菜 | Beef, Chicken, Lamb, Pork, Goat, Fish |
| 6 | 西式轻食 | Pasta, Seafood, Vegetarian, Vegan |
| 7 | 甜点饮品 | Dessert, Side, Starter |

### 3. 一键运行

```bash
python run_all.py
```

或者分步骤运行：

```bash
# Step 1: 爬取数据
python crawl_meal.py -v

# Step 2: 生成字典
python generate_dictionary.py

# Step 3: 批量处理（翻译+价格+分类）
python batch_process.py --min-price 15 --max-price 88 --seed 42

# Step 4: 上传图片到 MinIO
python download_and_upload.py --endpoint 127.0.0.1:9000 --bucket product-picture

# Step 5: 生成 SQL
python generate_sql.py
```

---

## 📊 数据库分类映射

数据库现有分类 + 新增西式分类：

| ID | 分类名 | 来源 | TheMealDB 分类 |
|----|--------|------|---------------|
| 1 | 招牌主菜 | 现有 | — |
| 2 | 精品小炒 | 现有 | Breakfast, Miscellaneous |
| 3 | 汤品 | 现有 | Soup |
| 4 | 主食 | 现有 | — |
| 5 | 西式主菜 | 新增 | Beef, Chicken, Lamb, Pork, Goat, Fish |
| 6 | 西式轻食 | 新增 | Pasta, Seafood, Vegetarian, Vegan |
| 7 | 甜点饮品 | 新增 | Dessert, Side, Starter |

---

## 📥 执行 SQL 导入

```bash
mysql -u root -p del_product_db < products_import.sql
```

---

## ⚠️ 注意事项

1. **网络环境**: 运行机器需要能访问 `www.themealdb.com`（HTTPS）
2. **MinIO 连接**: 确保 MinIO 服务正常运行
3. **分类数据**: 首次使用前需执行 `add_western_categories.sql` 添加新分类
4. **依赖安装**: `pip install requests minio`

---

## 🔄 数据流程图

```
TheMealDB API
     ↓
crawl_meal.py
     ↓
raw_dishes.jsonl
     ↓
generate_dictionary.py
     ↓
dish_dictionary.json (原始模板)
     ↓
batch_process.py (翻译+价格+分类)
     ↓
dish_dictionary.json (已处理)
     ↓
download_and_upload.py (下载图片+上传MinIO)
     ↓
dish_dictionary_final.json (含MinIO URL)
     ↓
generate_sql.py
     ↓
products_import.sql
     ↓
数据库 t_product 表
```

---

## 🔧 命令行参数

### batch_process.py
```
-i, --input      输入文件 (默认: dish_dictionary.json)
-o, --output     输出文件 (默认: 覆盖原文件)
--min-price      最低价格 (默认: 15.0)
--max-price      最高价格 (默认: 88.0)
--seed           随机种子 (用于复现)
```

### download_and_upload.py
```
-i, --input         数据字典文件 (默认: dish_dictionary.json)
-o, --output        输出文件 (默认: dish_dictionary_final.json)
-t, --temp-dir      临时目录 (默认: temp_images)
--endpoint          MinIO 地址 (默认: 127.0.0.1:9000)
--access-key        Access Key (默认: admin)
--secret-key        Secret Key (默认: sakana013)
--bucket            Bucket (默认: product-picture)
--secure            使用 HTTPS
-w, --workers       并发数 (默认: 5)
```
