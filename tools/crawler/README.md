# 菜品数据爬虫（Pipeline 架构）

基于 Pipeline 抽象模式的菜品数据爬虫，从 TheMealDB 爬取数据，上传图片到 MinIO，写入数据库。

## 📦 安装依赖

```bash
cd E:\Idea_project\delivery-cloud\tools\crawler
pip install -r requirements.txt
```

**注意**：如果权限不足，使用：
```bash
pip install --user -r requirements.txt
```

## ⚙️ 配置

编辑 `config.yaml`，填入数据库和 MinIO 配置：

```yaml
database:
  host: localhost
  port: 3306
  user: root
  password: your_password
  database: del_product_db

minio:
  endpoint: 127.0.0.1:9000
  access_key: admin
  secret_key: your_secret
  bucket: product-picture
  secure: false
```

## 🚀 使用

```bash
# 爬取 50 条，输出 JSON
python crawler.py --limit 50

# 直接导入数据库
python crawler.py --limit 50 --import

# 生成 SQL 文件
python crawler.py --limit 50 --output sql > import.sql

# Dry-run 测试
python crawler.py --limit 5 --dry-run

# 跳过图片上传
python crawler.py --limit 50 --skip-images
```

## 📁 文件结构

```
crawler/
├── crawler.py              # CLI 入口 (< 200 行)
├── pipeline/               # 数据处理流水线
│   ├── base.py             # Processor 抽象 + Pipeline 编排
│   ├── scraper.py          # TheMealDB 爬取
│   ├── image_uploader.py   # MinIO 图片上传
│   ├── llm_processor.py    # 🔮 LLM 预留扩展点
│   └── orchestrator.py     # Pipeline 工厂
├── db/
│   └── importer.py          # MySQL 导入器
├── config.yaml             # 配置文件
├── requirements.txt         # 依赖
└── output/                 # JSON 输出目录
```

## 🔌 Pipeline 架构

```
ScraperProcessor → ImageUploaderProcessor → [LLMProcessor]
```

每个 Processor 都是独立模块，可单独测试，LLMProcessor 为预留扩展点。

## ⚠️ 注意事项

1. **网络**：需要访问 themealdb.com
2. **MinIO**：确保服务运行中
3. **分类**：首次使用前先执行 `add_categories.sql`


---

## 🤖 LLM 数据改写（可选）

启用 LLM 后，会把 TheMealDB 的英文菜谱改写成中文菜品介绍。

### 启用步骤

#### 1. 获取 Qwen API Key

访问 https://dashscope.aliyun.com/ 注册并获取 API Key。

#### 2. 修改 config.yaml

```yaml
llm:
  enabled: true
  api_key: sk-你的真实key
  model: qwen-turbo    # 或 qwen-plus / qwen-max
```

#### 3. 运行

```powershell
$env:PYTHONIOENCODING = "utf-8"
python crawler.py --limit 20 --dry-run
```

日志会显示 LLM 处理进度和成本预估：
```
[LLM] 开始处理 20 项（模型: qwen-turbo, 并发: 5）
[LLM] 完成: 成功 20, 失败 0, 输入 8520 tokens, 输出 3120 tokens, 预估成本 ¥0.0254, 耗时 12.3s
```

### 成本参考

| 模型 | 输入价 | 输出价 | 100 条成本 | 1000 条成本 |
|------|--------|--------|-----------|-----------|
| qwen-turbo | ¥0.0008/千 | ¥0.002/千 | ~¥0.02 | ~¥0.2 |
| qwen-plus | ¥0.004/千 | ¥0.012/千 | ~¥0.1 | ~¥1 |
| qwen-max | ¥0.04/千 | ¥0.12/千 | ~¥1 | ~¥10 |

### 失败兜底

LLM 调用失败时会自动保留原始 description（不会让数据丢失）。

### 自定义 Prompt

在 config.yaml 中：
```yaml
llm:
  prompt: |
    你是米其林餐厅文案专家，语气高端大气。
    菜品：{name}...
```

Prompt 支持 4 个变量：`{name}` `{area}` `{category}` `{instructions}`