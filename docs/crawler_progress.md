# 菜品数据爬虫开发进度

> 更新日期：2026-08-29

## 概述

开发一个爬虫系统，从网上爬取菜品数据用于填充 delivery-cloud 系统的测试数据库。

## 当前状态

### ✅ 已完成

1. **项目结构创建**
   ```
   delivery-cloud/
   ├── scripts/
   │   └── crawler/
   │       ├── category_mapping.py    # 分类映射配置
   │       ├── meal_crawler.py        # 爬虫主程序（依赖 requests 库）
   │       ├── meal_crawler_mysql.py  # 直接插入 MySQL 版本
   │       ├── meal_crawler_builtin.py # 使用内置 urllib 版本
   │       ├── requirements.txt       # Python 依赖
   │       └── data/                 # 输出目录（SQL/JSON）
   └── docs/
       └── crawler_progress.md        # 本文档
   ```

2. **分类映射配置** (`category_mapping.py`)
   - TheMealDB 14 个分类到 t_category 的映射
   - 包含分类 ID、名称、排序

3. **测试数据库初始化脚本** (`init_test_db.sql`)
   - 创建 `delivery_test` 测试数据库
   - 创建 `t_category` 和 `t_product` 表结构

4. **数据映射方案**

   | 数据来源 | t_product 字段 | 处理方式 |
   |---------|---------------|---------|
   | TheMealDB / 模拟 | name | 直接映射 / 随机生成 |
   | TheMealDB / 模拟 | cover | 图片URL / Picsum占位图 |
   | TheMealDB | categoryId | 通过分类映射表转换 |
   | TheMealDB | description | 地区 + 食材 + 烹饪步骤 |
   | 模拟生成 | fid | UUID |
   | 模拟生成 | normPrice | 随机 15.0~99.0 |
   | 模拟生成 | realPrice | 随机 12.0~79.0 |
   | 模拟生成 | stock | 默认 100 |
   | 模拟生成 | sales | 随机 0~500 |
   | 模拟生成 | status | 默认 0（上架）|

### ⚠️ 遇到的问题

**沙盒环境网络受限**
- WinError 10013：无法访问外部网络（TheMealDB API）
- 无法直接 pip install 依赖包
- 无法连接 localhost MySQL 进行测试

**解决方案**：使用本地模拟数据生成器，不依赖外部网络

## 待完成

### 1. 模拟数据生成器 (`generate_mock_data.py`)
- 状态：代码已编写，等待执行
- 功能：
  - 生成 500 条模拟菜品数据
  - 生成 14 个分类数据
  - 输出 SQL 脚本文件

### 2. SQL 脚本执行
- 状态：待执行
- 步骤：
  1. 执行 `init_test_db.sql` 创建测试库和表
  2. 执行 `insert_categories.sql` 插入分类
  3. 执行 `insert_products.sql` 插入菜品

### 3. 数据验证
- 状态：待执行
- 验证项：
  - 分类表数据完整性
  - 菜品表数据完整性
  - 数据关联正确性

## 使用方法

### 方式一：本地执行（推荐）

1. **安装依赖**
   ```bash
   cd E:\Idea_project\delivery-cloud\scripts\crawler
   pip install requests mysql-connector-python
   ```

2. **运行模拟数据生成器**
   ```bash
   python generate_mock_data.py
   ```

3. **执行 SQL 脚本**
   ```bash
   # 登录 MySQL
   mysql -u root -p
   
   # 执行初始化
   SOURCE E:/Idea_project/delivery-cloud/scripts/crawler/data/insert_categories.sql;
   SOURCE E:/Idea_project/delivery-cloud/scripts/crawler/data/insert_products.sql;
   ```

4. **验证数据**
   ```sql
   USE delivery_test;
   SELECT COUNT(*) FROM t_category;  -- 应为 14
   SELECT COUNT(*) FROM t_product;   -- 应为 500
   ```

### 方式二：直接导入 SQL

1. 使用 `init_test_db.sql` 创建测试库
2. 使用生成的 SQL 脚本导入数据

## 后续拓展方向

1. **真实数据源对接**
   - 大众点评 API（需申请）
   - 美团开放平台（需申请）
   - 其他菜谱网站

2. **增量更新**
   - 定时任务自动更新
   - 增量爬取而非全量

3. **数据丰富**
   - 添加评分字段
   - 添加商家信息
   - 添加评价数量
   - 添加详细营养成分

4. **生产库接入**
   - 验证脚本稳定性后
   - 接入正式业务数据库

## 文件清单

| 文件路径 | 说明 | 状态 |
|---------|------|------|
| `scripts/crawler/category_mapping.py` | 分类映射配置 | ✅ |
| `scripts/crawler/meal_crawler.py` | 爬虫主程序 | ✅ |
| `scripts/crawler/meal_crawler_mysql.py` | MySQL直插版 | ✅ |
| `scripts/crawler/meal_crawler_builtin.py` | urllib版 | ✅ |
| `scripts/crawler/generate_mock_data.py` | 模拟数据生成器 | ✅ |
| `scripts/crawler/requirements.txt` | Python依赖 | ✅ |
| `scripts/crawler/init_test_db.sql` | 测试库初始化 | ✅ |
| `scripts/crawler/data/` | SQL输出目录 | ⏳ 待生成 |

## 备注

- 测试数据库：`delivery_test`
- 目标数据量：500 条菜品 + 14 个分类
- 数据来源：TheMealDB API（网络限制时使用模拟数据）
