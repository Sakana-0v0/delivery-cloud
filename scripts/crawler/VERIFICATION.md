# 🐍 爬虫数据入库验证文档

> **创建时间**：2026-09-02  
> **脚本目录**：E:\Idea_project\delivery-cloud\scripts\crawler  
> **数据源**：TheMealDB API (https://www.themealdb.com/api/json/v1/1/)

---

## 📋 执行清单

### 第一阶段：环境准备

| # | 检查项 | 状态 | 说明 |
|---|--------|------|------|
| 1 | Python 环境 | ⬜ 未检查 | 确认 python --version ≥ 3.8 |
| 2 | pip 依赖安装 | ⬜ 未检查 | pip install -r requirements.txt |
| 3 | MinIO 服务运行 | ⬜ 未检查 | http://127.0.0.1:9000 可访问 |
| 4 | MySQL 服务运行 | ⬜ 未检查 | del_product_db 数据库存在 |
| 5 | 新分类已添加 | ⬜ 未检查 | 执行 dd_western_categories.sql 后检查 |

### 第二阶段：脚本执行

| # | 步骤 | 脚本 | 状态 | 日志文件 |
|---|------|------|------|---------|
| 1 | 爬取原始数据 | crawl_meal.py | ⬜ 未执行 | aw_dishes.jsonl |
| 2 | 生成数据字典模板 | generate_dictionary.py | ⬜ 未执行 | dish_dictionary.json |
| 3 | 批量处理（翻译+价格+分类） | atch_process.py | ⬜ 未执行 | 覆盖 dish_dictionary.json |
| 4 | 下载图片并上传 MinIO | download_and_upload.py | ⬜ 未执行 | dish_dictionary_final.json |
| 5 | 生成入库 SQL | generate_sql.py | ⬜ 未执行 | products_import.sql |

### 第三阶段：数据验证

#### 3.1 文件检查

| # | 检查项 | 预期 | 状态 |
|---|--------|------|------|
| 1 | aw_dishes.jsonl 存在 | 文件存在且 > 0 | ⬜ |
| 2 | aw_dishes.jsonl 条数 | ≥ 500 条 | ⬜ |
| 3 | dish_dictionary.json 存在 | 文件存在且 > 0 | ⬜ |
| 4 | dish_dictionary_final.json 存在 | 文件存在且 > 0 | ⬜ |
| 5 | products_import.sql 存在 | 文件存在且 > 0 | ⬜ |

#### 3.2 MinIO 检查

| # | 检查项 | 预期 | 状态 |
|---|--------|------|------|
| 1 | product-picture bucket 存在 | bucket 已创建 | ⬜ |
| 2 | bucket 权限 | public-read 或 presigned URL 可用 | ⬜ |
| 3 | 上传图片数量 | ≥ 500 张 | ⬜ |
| 4 | 图片可访问 | 随机抽 5 张验证 URL 可访问 | ⬜ |

#### 3.3 数据库检查

| # | 检查项 | SQL | 状态 |
|---|--------|-----|------|
| 1 | 分类数量 | SELECT COUNT(*) FROM t_category; → 7 | ⬜ |
| 2 | 商品数量 | SELECT COUNT(*) FROM t_product; → ≥ 500 | ⬜ |
| 3 | 商品有价格 | SELECT norm_price FROM t_product WHERE norm_price = 0; → 0 | ⬜ |
| 4 | 商品有分类 | SELECT category_id IS NULL FROM t_product; → 0 | ⬜ |
| 5 | 商品有图片 URL | SELECT cover FROM t_product WHERE cover = '' OR cover IS NULL; → 0 | ⬜ |

### 第四阶段：前端验证

| # | 检查项 | 预期 | 状态 |
|---|--------|------|------|
| 1 | 商品列表页加载 | ≥ 10 个商品显示 | ⬜ |
| 2 | 商品图片加载 | 图片正常显示 | ⬜ |
| 3 | 分类筛选 | 各分类下有商品 | ⬜ |
| 4 | 商品详情页 | 图片 + 描述正常 | ⬜ |

---

## 🔧 MinIO 配置信息

| 配置项 | 值 |
|--------|-----|
| Endpoint | 127.0.0.1:9000 |
| Access Key | dmin |
| Secret Key | sakana013 |
| Bucket | product-picture |
| 协议 | HTTP（非 HTTPS）|

---

## 🗄️ 数据库配置

| 配置项 | 值 |
|--------|-----|
| 数据库 | del_product_db |
| 商品表 | 	_product |
| 分类表 | 	_category |
| 文件表 | del_file_db.t_file_info |

---

## 📊 分类映射表

| TheMealDB 分类 | 数据库分类 ID | 分类名 |
|----------------|-------------|--------|
| Beef / Chicken / Pork / Lamb / Goat / Fish | 5 | 西式主菜 |
| Breakfast / Miscellaneous | 2 | 精品小炒 |
| Soup | 3 | 汤品 |
| Pasta / Seafood / Vegetarian / Vegan | 6 | 西式轻食 |
| Dessert / Side / Starter | 7 | 甜点饮品 |

---

## 🚀 推荐执行命令

`ash
cd E:\Idea_project\delivery-cloud\scripts\crawler

# 步骤1：安装依赖
pip install -r requirements.txt

# 步骤2：添加新分类（仅执行一次）
mysql -u root -psakana013 del_product_db < add_western_categories.sql

# 步骤3：一键执行爬取
python run_all.py

# 步骤4：导入数据库
mysql -u root -psakana013 del_product_db < products_import.sql
`

---

## 📝 执行记录

### 首次执行

| 时间 | 操作 | 结果 | 备注 |
|------|------|------|------|
| - | - | - | - |

---

## ⚠️ 常见问题

| 问题 | 原因 | 解决方案 |
|------|------|----------|
| TheMealDB 无法访问 | 网络限制 / HTTPS 拦截 | 检查网络代理 |
| MinIO 上传失败 | Bucket 不存在 | 先运行 download_and_upload.py 自动创建 |
| 商品图片为空 | MinIO URL 未写入数据库 | 检查 generate_sql.py 是否正确读取 MinIO URL |
| 分类 ID 不匹配 | category_mapping.py 分类 ID 错误 | 检查映射表与数据库 	_category.id 是否一致 |

---

*最后更新：2026-09-02*
