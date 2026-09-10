# 📋 工单 #CRAWLER-002：Python 爬虫脚本化实施（重构方案）

> **创建时间**：2026-09-05
> **重构自**：#CRAWLER-001（封档）
> **接收方**：Python 工程师
> **验收方**：Codex（架构师）
> **预计工时**：1.5-2 天（4 个 Iteration 串行）
> **架构选型**：Python 3.11 + 标准库 + httpx + minio + pymysql

---

## 一、需求与目标

### 1.1 业务目标

将外部公开菜品数据（TheMealDB）爬取后**导入到 del_product_db.t_product 表**，扩充商品库。

### 1.2 与 #CRAWLER-001 的核心差异

| 维度 | 原方案（服务）| 新方案（脚本）|
|------|------------|--------------|
| 运行方式 | 长期服务 | 手动执行脚本 |
| 调度 | Spring Scheduler | 无（用户手动）|
| 状态持久化 | MySQL `t_crawler_job` 表 | 不需要 |
| UI | Vue 页面 | 无 |
| MQ | RabbitMQ | 无 |
| Docker | 需要 | 不需要 |
| **数据流** | TheMealDB → MinIO → Preview → Approve → DB | **完全不变** |

### 1.3 设计不变的部分

- ✅ **Pipeline 抽象模式**（继承自 #PROD-VOTE-008 的设计哲学）
- ✅ **LLM 扩展点**（已设计为可插拔）
- ✅ **MinIO 图片存储**
- ✅ **JSON 输出作为人工审核的载体**

---

## 二、最终架构

```
┌────────────────────────────────────────────────────────┐
│  Python 脚本 (手动执行)                                  │
│                                                         │
│   $ python crawler.py --categories Beef --limit 50      │
│                                                         │
│   ┌─────────────────────────────────────────────────┐ │
│   │  Pipeline (Processor 链)                         │ │
│   │                                                   │ │
│   │  ScraperProcessor                                 │ │
│   │    ↓ TheMealDB API                                │ │
│   │  ImageUploaderProcessor                           │ │
│   │    ↓ MinIO                                        │ │
│   │  [LLMProcessor] 🔮 预留                            │ │
│   │    ↓                                              │ │
│   │  ResultFormatter                                   │ │
│   │    ↓                                              │ │
│   │  Output (JSON 文件 / SQL 文件 / 直接 INSERT)     │ │
│   └─────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────┘
```

---

## 三、文件结构

```
E:\Idea_project\delivery-cloud\tools\crawler\
├── README.md                       # 使用文档
├── requirements.txt                 # Python 依赖
├── config.yaml.example             # 配置示例
├── config.yaml                     # 真实配置（git ignore）
├── crawler.py                      # CLI 入口
├── pipeline/
│   ├── __init__.py
│   ├── base.py                     # 抽象 Processor
│   ├── orchestrator.py             # Pipeline 编排
│   ├── scraper.py                  # TheMealDB
│   ├── image_uploader.py           # MinIO
│   └── llm_processor.py            # 🔮 预留 LLM
├── db/
│   ├── __init__.py
│   ├── importer.py                 # MySQL 导入器
│   └── schema.sql                  # t_product 表结构参考
├── output/
│   ├── .gitkeep
│   └── products_*.json             # 运行时生成
└── tests/
    ├── __init__.py
    ├── test_pipeline.py
    └── test_scraper.py
```

---

## 四、Iteration 详细规划

### 🟢 Iteration 1：MVP 脚本（0.5 天）

**目标**：能跑通 TheMealDB → JSON 输出

#### 4.1.1 任务清单

| # | 任务 | 产出文件 | 验收点 |
|---|------|---------|--------|
| 1.1 | 创建项目骨架 | `tools/crawler/` 目录 + `__init__.py` | 目录结构符合上文 |
| 1.2 | 写 `requirements.txt` | `requirements.txt` | 含 httpx, pymysql, minio, pyyaml, click |
| 1.3 | 写 `config.yaml.example` | `config.yaml.example` | 含完整注释、所有配置项 |
| 1.4 | 实现 `pipeline/base.py` 抽象 Processor | `pipeline/base.py` | 含 `Processor` 抽象类 + `Pipeline` 类 |
| 1.5 | 实现 `pipeline/scraper.py` TheMealDB 爬取 | `pipeline/scraper.py` | 能从 TheMealDB 拉数据 |
| 1.6 | 实现 `pipeline/image_uploader.py` MinIO 上传 | `pipeline/image_uploader.py` | 能上传图片并返回 URL |
| 1.7 | 实现 `pipeline/orchestrator.py` | `pipeline/orchestrator.py` | 能编排 Processor |
| 1.8 | 实现 `crawler.py` CLI 入口 | `crawler.py` | 能解析参数 + 启动 pipeline |
| 1.9 | 输出 JSON 到 `output/` | (crawler.py 内) | JSON 文件可读 |
| 1.10 | 写 README | `README.md` | 含安装、使用、故障排查 |

#### 4.1.2 关键技术约束

**Pipeline 抽象**（必须严格遵守）：
```python
class Processor(ABC):
    @abstractmethod
    async def process(self, item: ProductItem) -> ProductItem:
        pass
```

**数据模型**：
```python
@dataclass
class ProductItem:
    source_id: str
    name: str
    description: str
    category: str
    cover_url: Optional[str] = None
    price: Optional[float] = None
    extra: dict = field(default_factory=dict)
```

**TheMealDB API 使用**：
- 列表：`https://www.themealdb.com/api/json/v1/1/filter.php?c={category}`
- 详情：`https://www.themealdb.com/api/json/v1/1/lookup.php?i={meal_id}`
- 注意：TheMealDB 有速率限制，加 `await asyncio.sleep(0.5)` 防 429

**MinIO 上传**：
- 复用现有 bucket：`product-picture`
- 文件名规则：`crawler/{mealId}.jpg`
- URL 格式：`http://minio:9000/product-picture/crawler/{mealId}.jpg`

#### 4.1.3 CLI 设计

```bash
python crawler.py [OPTIONS]

Options:
  --config PATH          配置文件路径 (默认 ./config.yaml)
  --categories TEXT      逗号分隔分类 (覆盖 config)
  --limit INT            最大爬取数量 (覆盖 config)
  --dry-run              只爬取不上传不导入
  --import               直接导入数据库
  --yes                  跳过确认提示
  --help                 显示帮助
```

#### 4.1.4 Iteration 1 验收清单（我会逐项检查）

- [ ] 所有文件存在于 `E:\Idea_project\delivery-cloud\tools\crawler\` 对应路径
- [ ] `requirements.txt` 包含必要依赖
- [ ] `pipeline/base.py` 有完整的 Processor 和 Pipeline 类
- [ ] `pipeline/scraper.py` 实现了 TheMealDB 的 list + detail API
- [ ] `pipeline/image_uploader.py` 使用 minio SDK 上传
- [ ] `crawler.py` 可以 `python crawler.py --help` 显示帮助
- [ ] `crawler.py` 可以 dry-run 跑通并输出 JSON
- [ ] JSON 文件包含：source_id, name, description, category, cover_url
- [ ] README 包含安装命令、使用示例、常见错误

**验收方式**：我会在终端跑以下命令验证
```bash
cd E:\Idea_project\delivery-cloud\tools\crawler
pip install -r requirements.txt
python crawler.py --help
python crawler.py --categories Beef --limit 5 --dry-run
ls output/
```

---

### 🟡 Iteration 2：直接入库 + Dry-run + 配置增强（0.5 天）

**目标**：脚本能直接写 DB，且支持幂等去重

#### 4.2.1 任务清单

| # | 任务 | 产出文件 | 验收点 |
|---|------|---------|--------|
| 2.1 | 实现 `db/importer.py` | `db/importer.py` | 能批量 INSERT t_product |
| 2.2 | 实现去重逻辑 | (在 importer.py) | 基于 source_id 跳过已存在 |
| 2.3 | 实现交互式确认 | `crawler.py` | `--import` 前确认 (除非 `--yes`) |
| 2.4 | SQL 文件生成 | `crawler.py` 选项 | 输出可审计的 SQL |
| 2.5 | `--resume` 模式 | `crawler.py` | 支持断点续跑 |
| 2.6 | 写 `db/schema.sql` | `db/schema.sql` | 含 t_product 表 DDL |
| 2.7 | 错误日志 | `output/errors.log` | 记录失败项详情 |

#### 4.2.2 数据库表设计参考

读取现有 `t_product` 表结构（不创建新表）：

```sql
-- 复用现有表，crawler 不创建新表
-- INSERT 时字段映射：
--   source_id    -> 存储在 extra.source_id（JSON 字段）
--   name         -> product_name
--   description  -> product_desc
--   category     -> 需要查 category_id（基于 category name）
--   cover_url    -> cover（完整 URL）
--   price        -> price（默认 0.00）
```

#### 4.2.3 去重策略

- INSERT 前 `SELECT` 检查 `(extra->>'source_id')` 是否已存在
- 跳过已存在项，记录到 `output/skipped.log`
- 不更新已有项（只 INSERT 不 UPDATE）

#### 4.2.4 关键代码片段要求

```python
class MysqlImporter:
    def __init__(self, config: dict):
        self.conn = pymysql.connect(...)
    
    def import_batch(self, items: list[ProductItem]) -> ImportResult:
        """
        批量导入，返回成功/失败/跳过数量
        """
        # 1. 查 category_id 映射
        # 2. 查重（基于 source_id）
        # 3. 批量 INSERT
        # 4. 返回 ImportResult
```

#### 4.2.5 Iteration 2 验收清单

- [ ] `db/importer.py` 实现完整
- [ ] 能用 `python crawler.py --categories Beef --limit 5 --import --yes` 成功入库
- [ ] 同一批数据跑两次，第二次全部 SKIPPED
- [ ] SQL 文件能生成且语法正确
- [ ] 错误日志能正确记录
- [ ] 数据库记录数与预期一致

**验收方式**：
```bash
# 1. 入库测试
python crawler.py --categories Beef --limit 5 --import --yes

# 2. 查 DB 确认
mysql -e "SELECT id, product_name, cover FROM del_product_db.t_product WHERE cover LIKE '%crawler%';"

# 3. 重复跑验证去重
python crawler.py --categories Beef --limit 5 --import --yes
# 预期：日志显示 5 skipped
```

---

### 🟠 Iteration 3：LLM 扩展点（0.25 天）

**目标**：预留 LLM 接入，但不实际实现

#### 4.3.1 任务清单

| # | 任务 | 产出文件 | 验收点 |
|---|------|---------|--------|
| 3.1 | 实现 `LLMProcessor` 骨架 | `pipeline/llm_processor.py` | 空 `process()` 方法 + TODO 注释 |
| 3.2 | config.yaml 增加 LLM 段 | `config.yaml.example` | 含 `llm:` 配置块 |
| 3.3 | orchestrator 支持 LLM 开关 | `pipeline/orchestrator.py` | 根据 config 决定是否启用 |
| 3.4 | 写扩展文档 | `README.md` 段落 | "如何接入 LLM" 步骤说明 |

#### 4.3.2 LLMProcessor 骨架

```python
class LLMProcessor(Processor):
    """
    🔮 预留 LLM 接入点
    未来启用：在 orchestrator 中取消注释
    """
    
    def __init__(self, api_key: str, model: str, prompt: str):
        self.api_key = api_key
        self.model = model
        self.prompt = prompt
        # TODO: 实现 LLM 调用
    
    async def process(self, item: ProductItem) -> ProductItem:
        # TODO: 调用 LLM API 清洗/翻译 item
        raise NotImplementedError("LLM 处理器尚未实现")
```

#### 4.3.3 config.yaml LLM 段

```yaml
# 🔮 LLM 处理（未来启用）
llm:
  enabled: false
  api_key: ${OPENAI_API_KEY}    # 从环境变量读取
  base_url: https://api.openai.com/v1
  model: gpt-4
  prompt: |
    将以下英文菜名翻译为中文，并生成 50 字以内的中文描述。
    返回 JSON 格式：{"name_zh": "...", "description_zh": "..."}
```

#### 4.3.4 Iteration 3 验收清单

- [ ] `LLMProcessor` 类存在且实现空 `process()`
- [ ] config.yaml.example 包含完整 LLM 配置块
- [ ] `pipeline/orchestrator.py` 根据 `llm.enabled` 决定是否启用
- [ ] 当 `llm.enabled = false` 时脚本正常运行（不报错）
- [ ] 当 `llm.enabled = true` 时给出明确 NotImplementedError 提示
- [ ] README 含"未来扩展 LLM"段落，3-5 步说明

---

### 🔴 Iteration 4：生产化增强（按需，本次可跳过）

**本次不实现**，等用户用 Iteration 1-3 跑过几次后再决定。

**可选增强**：
- 异步并发爬取（asyncio.gather）
- 失败重试机制（tenacity）
- HTML 预览页（基于 Jinja2）
- 进度条（rich 库）

---

## 五、代码规范（必须遵守）

### 5.1 命名规范

- **文件**：snake_case（如 `image_uploader.py`）
- **类**：PascalCase（如 `ImageUploaderProcessor`）
- **函数/变量**：snake_case
- **常量**：UPPER_SNAKE_CASE

### 5.2 日志规范

使用 `logging` 标准库，格式：
```python
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s"
)
```

每个 Processor 必须打印：
- `[开始]` 进入 process 时
- `[完成]` 退出 process 时（带耗时）

### 5.3 错误处理

- 网络错误（httpx.Timeout / ConnectError）：捕获并重试 3 次
- MinIO 上传失败：跳过该项，记录到 `errors.log`
- DB 写入失败：事务回滚，记录到 `errors.log`
- 不静默吞异常

### 5.4 类型注解

**必须**使用 Python 3.11+ 的类型注解：

```python
async def process(self, item: ProductItem) -> ProductItem:
    pass
```

---

## 六、验收节奏

| 阶段 | 工程师交付 | 我验收 |
|------|-----------|--------|
| Iteration 1 完成 | 提交代码 + 跑通截图 + JSON 示例 | 我跑命令验证 + 看代码 |
| Iteration 2 完成 | 提交代码 + DB 截图 | 我跑导入命令 + 查 DB |
| Iteration 3 完成 | 提交代码 + README 段落 | 我看代码 + 测 LLM 开关 |

**每次交付要求**：
- 全部代码 commit 到 git
- 提供运行截图或日志输出
- 说明跑通的命令

---

## 七、风险与降级

| 风险 | 降级方案 |
|------|----------|
| TheMealDB 限流 | 脚本内置 sleep 0.5s |
| MinIO 连接失败 | 跳过该项继续，最后汇总失败数 |
| DB 写入冲突 | 单条失败不影响其他，回滚用 try/except |
| Python 环境问题 | README 写明 Python 3.11+ 要求 |
| 配置文件写错 | 启动时校验必需字段，给出明确报错 |

---

## 八、最终交付清单

完成 4 个 Iteration 后，工程师应交付：

- [ ] `E:\Idea_project\delivery-cloud\tools\crawler\` 完整目录
- [ ] 所有源文件（含类型注解和注释）
- [ ] `requirements.txt`
- [ ] `config.yaml.example`
- [ ] `README.md`（含安装、使用、扩展、故障排查）
- [ ] `output/` 目录（含至少一个成功生成的 JSON 示例）
- [ ] Git commit 信息清晰

---

## 九、签收

| 角色 | 操作 | 时间 |
|------|------|------|
| 工单创建 | Codex | 2026-09-05 |
| Python 执行 | _待填_ | |
| Iteration 1 验收 | Codex | |
| Iteration 2 验收 | Codex | |
| Iteration 3 验收 | Codex | |

---

## 附录：CLI 使用示例

```bash
# 1. 安装依赖
cd E:\Idea_project\delivery-cloud\tools\crawler
pip install -r requirements.txt

# 2. 复制配置
cp config.yaml.example config.yaml
# 编辑 config.yaml 填入 TheMealDB 配置、MinIO 配置、MySQL 配置

# 3. Dry-run 测试（不写任何文件）
python crawler.py --categories Beef --limit 5 --dry-run

# 4. 爬取并输出 JSON
python crawler.py --categories Beef --limit 50

# 5. 查看 JSON
cat output/products_*.json | python -m json.tool | less

# 6. 确认后直接入库
python crawler.py --categories Beef --limit 50 --import

# 7. 生成 SQL 文件人工审核
python crawler.py --categories Beef --limit 50 --output-format sql > import.sql
mysql -u root -p del_product_db < import.sql
```