# 📋 工单 #CRAWLER-002-R1：Pipeline 抽象重构

> **创建时间**：2026-09-05
> **关联工单**：#CRAWLER-002
> **接收方**：Python 工程师
> **验收方**：Codex
> **预计工时**：1.5 小时
> **依赖**：#CRAWLER-002 Iteration 1+2 已交付

---

## 一、为什么需要重构

#CRAWLER-002 已交付并通过功能验收，但**未实现原方案要求的 Pipeline 抽象模式**。当前是单文件 14KB，三个独立 Client 类直接调用。

### 重构的必要性

| 不重构的后果 | 严重程度 |
|------------|---------|
| Iteration 3（LLM 扩展点）需要拆散整个文件 | 🔴 1 天额外工作量 |
| 无法独立测试 Scraper / ImageUploader | ⚠️ 单元测试困难 |
| Scraper 与 ImageUploader 紧耦合 | ⚠️ 维护成本高 |
| 后续加新 Stage（如翻译/打标）要改 main 流程 | ⚠️ 违反开闭原则 |

### 重构的收益

| 收益 | 程度 |
|------|------|
| Iteration 3 加 LLM = **1 行代码** | 🟢 极大简化 |
| 每个 Processor 可独立测试 | 🟢 质量提升 |
| 文件职责清晰 | 🟢 可读性提升 |
| 复用 #PROD-VOTE-008 已有 Pipeline 设计哲学 | 🟢 架构一致性 |

---

## 二、重构目标

**保留所有现有功能**，**只重构代码组织方式**：

| 功能 | 必须保留 |
|------|---------|
| Dry-run | ✅ |
| JSON 输出 | ✅ |
| SQL 输出 | ✅ |
| --import 直接入库 | ✅ |
| 去重逻辑 | ✅ |
| 错误处理 | ✅ |
| 日志 | ✅ |
| 配置加载 | ✅ |
| **Pipeline 抽象模式** | 🆕 必须新增 |

---

## 三、目标文件结构

```
E:\Idea_project\delivery-cloud\tools\crawler\
├── README.md                       # 不变
├── requirements.txt                 # 不变
├── config.yaml                      # 不变
├── config.yaml.example              # 不变
├── add_categories.sql               # 不变
├── output/                          # 不变
├── crawler.py                       # 🔧 瘦身为 CLI 入口
├── pipeline/                        # 🆕 新建目录
│   ├── __init__.py                  # 🆕 导出 Pipeline / Processor
│   ├── base.py                      # 🆕 抽象 Processor + Pipeline
│   ├── scraper.py                   # 🆕 ScraperProcessor（从 crawler.py 拆出）
│   ├── image_uploader.py            # 🆕 ImageUploaderProcessor（拆出）
│   ├── llm_processor.py             # 🆕 LLMProcessor（骨架，NotImplementedError）
│   └── orchestrator.py              # 🆕 build_pipeline() 工厂
└── db/
    ├── __init__.py                  # 🆕
    └── importer.py                  # 🆕 DatabaseClient（从 crawler.py 拆出）
```

---

## 四、详细代码骨架

### 4.1 `pipeline/base.py`（约 50 行）

```python
"""
Pipeline 抽象层 — 所有数据处理 Stage 的基类

设计参考 #PROD-VOTE-008 的状态机思想，
将数据处理拆成可插拔的 Processor。
"""
from abc import ABC, abstractmethod
from typing import List
from dataclasses import dataclass, field
from typing import Optional


@dataclass
class ProductItem:
    """统一的菜品数据模型"""
    source_id: str
    name: str
    description: str
    category: str
    area: str = "Other"
    cover_url: Optional[str] = None       # TheMealDB 原 URL
    minio_cover: Optional[str] = None     # MinIO URL（Stage 2 填充）
    category_id: Optional[int] = None
    category_name: Optional[str] = None
    norm_price: float = 0.0
    real_price: float = 0.0
    stock: int = 999
    sales: int = 0
    status: int = 1
    fid: str = field(default_factory=lambda: __import__('uuid').uuid4().hex)
    extra: dict = field(default_factory=dict)


class Processor(ABC):
    """所有处理阶段的抽象基类"""
    
    def __init__(self, config: dict = None):
        self.config = config or {}
        self.name = self.__class__.__name__
    
    @abstractmethod
    def process(self, items: List[ProductItem]) -> List[ProductItem]:
        """
        处理一批商品
        :param items: 输入商品列表
        :return: 处理后的商品列表（可能更少，失败的被过滤）
        """
        pass
    
    def __repr__(self):
        return f"<{self.name}>"


class Pipeline:
    """Pipeline 编排器 — 按顺序执行所有 Processor"""
    
    def __init__(self):
        self.processors: List[Processor] = []
    
    def add(self, processor: Processor) -> 'Pipeline':
        self.processors.append(processor)
        return self
    
    def run(self, items: List[ProductItem]) -> List[ProductItem]:
        """依次执行所有 Processor"""
        for p in self.processors:
            logger.info(f"[Pipeline] 执行 {p.name}（{len(items)} 项）")
            items = p.process(items)
            logger.info(f"[Pipeline] {p.name} 完成（剩余 {len(items)} 项）")
        return items
```

### 4.2 `pipeline/scraper.py`（约 130 行）

```python
"""
Stage 1: 从 TheMealDB 爬取原始数据并转换为 ProductItem
"""
import time
import requests
import logging
from typing import List

from pipeline.base import Processor, ProductItem

logger = logging.getLogger('crawler')

class ScraperProcessor(Processor):
    """TheMealDB 数据爬取处理器"""
    
    BASE_URL = "https://www.themealdb.com/api/json/v1/1"
    
    CATEGORY_MAP = {
        "Beef": 5, "Breakfast": 2, "Chicken": 5, "Dessert": 7,
        "Goat": 5, "Lamb": 5, "Miscellaneous": 2, "Pasta": 6,
        "Pork": 5, "Seafood": 6, "Side": 7, "Starter": 7,
        "Vegan": 6, "Vegetarian": 6, "Fish": 5, "Soup": 3,
    }
    
    PRICE_RANGES = {
        "Japanese": (35, 68), "Italian": (38, 78), "Chinese": (25, 55),
        # ... 完整复制
        "Other": (20, 55),
    }
    
    def __init__(self, config: dict = None):
        super().__init__(config)
        self.session = requests.Session()
        self.session.headers.update({
            'User-Agent': 'Mozilla/5.0 (compatible; CrawlerBot/1.0)'
        })
    
    def process(self, items: List[ProductItem] = None) -> List[ProductItem]:
        """爬取数据"""
        limit = self.config.get('limit', 50)
        categories = self.config.get('categories', None)
        
        logger.info(f"[Scraper] 开始爬取 (limit={limit})")
        raw_meals = self._crawl(categories, limit)
        logger.info(f"[Scraper] 获取 {len(raw_meals)} 条原始数据")
        
        # 转换为 ProductItem
        items = [self._transform(m) for m in raw_meals]
        items = [i for i in items if i is not None]
        logger.info(f"[Scraper] 转换 {len(items)} 条 ProductItem")
        
        return items
    
    def _crawl(self, categories, limit) -> list:
        """按字母 A-Z 或指定分类爬取"""
        if categories:
            return self._crawl_by_category(categories, limit)
        else:
            return self._crawl_all(limit)
    
    def _crawl_all(self, limit) -> list:
        # 原 crawl_all 逻辑迁移到这里
        ...
    
    def _crawl_by_category(self, categories, limit) -> list:
        # 按分类爬取（可选实现）
        ...
    
    def _transform(self, meal: dict) -> ProductItem:
        # 原 transform_meal 逻辑
        ...
    
    def _search_by_letter(self, letter: str) -> list:
        # 原 search_by_first_letter 逻辑
        ...
    
    def _get_detail(self, meal_id: str) -> dict:
        # 原 get_meal_detail 逻辑
        ...
```

### 4.3 `pipeline/image_uploader.py`（约 80 行）

```python
"""
Stage 2: 下载图片并上传到 MinIO
"""
import io
import time
import logging
from typing import List
import requests
from minio import Minio
from minio.error import S3Error

from pipeline.base import Processor, ProductItem

logger = logging.getLogger('crawler')


class ImageUploaderProcessor(Processor):
    """图片下载并上传到 MinIO"""
    
    def __init__(self, config: dict = None):
        super().__init__(config)
        minio_cfg = config.get('minio', {})
        self.client = Minio(
            minio_cfg['endpoint'],
            access_key=minio_cfg['access_key'],
            secret_key=minio_cfg['secret_key'],
            secure=minio_cfg.get('secure', False)
        )
        self.bucket = minio_cfg['bucket']
        self.session = requests.Session()
    
    def process(self, items: List[ProductItem]) -> List[ProductItem]:
        """处理一批商品的图片"""
        logger.info(f"[ImageUploader] 上传 {len(items)} 张图片")
        success = 0
        for item in items:
            if not item.cover_url:
                continue
            try:
                url = self._upload(item.cover_url, item.source_id)
                item.minio_cover = url
                success += 1
                time.sleep(0.2)
            except Exception as e:
                logger.warning(f"[ImageUploader] 上传失败 {item.name}: {e}")
        logger.info(f"[ImageUploader] 成功 {success}/{len(items)}")
        return items
    
    def _upload(self, url: str, meal_id: str) -> str:
        # 原 MinIOClient.upload_image 逻辑迁移
        ...
```

### 4.4 `pipeline/llm_processor.py`（约 30 行，骨架）

```python
"""
Stage 3: LLM 数据清洗与翻译（🔮 预留）

未来启用方法：在 orchestrator.py 中取消注释
    pipeline.add(LLMProcessor(config))
"""
import logging
from typing import List

from pipeline.base import Processor, ProductItem

logger = logging.getLogger('crawler')


class LLMProcessor(Processor):
    """
    🔮 LLM 处理器骨架 — 当前未实现
    
    未来实现要点：
    - 调用 OpenAI / 国产 LLM API
    - 翻译英文菜名为中文
    - 生成中文描述
    - 清洗 / 格式化数据
    """
    
    def __init__(self, config: dict = None):
        super().__init__(config)
        llm_cfg = config.get('llm', {})
        self.api_key = llm_cfg.get('api_key', '')
        self.model = llm_cfg.get('model', 'gpt-4')
        self.base_url = llm_cfg.get('base_url', 'https://api.openai.com/v1')
        self.prompt_template = llm_cfg.get('prompt', '')
    
    def process(self, items: List[ProductItem]) -> List[ProductItem]:
        # TODO: 未来实现
        # 1. 遍历 items
        # 2. 调用 LLM API
        # 3. 更新 item.name 和 item.description 为中文
        # 4. 异常时跳过该项
        raise NotImplementedError(
            "LLMProcessor 尚未实现。"
            "未来启用：在 config.yaml 设置 llm.enabled=true 并实现 process() 方法"
        )
```

### 4.5 `pipeline/orchestrator.py`（约 40 行）

```python
"""
Pipeline 工厂 — 根据配置组装 Processor 链
"""
import logging
from typing import List

from pipeline.base import Pipeline, ProductItem
from pipeline.scraper import ScraperProcessor
from pipeline.image_uploader import ImageUploaderProcessor
from pipeline.llm_processor import LLMProcessor

logger = logging.getLogger('crawler')


def build_pipeline(config: dict) -> Pipeline:
    """根据配置构建 Pipeline"""
    pipeline = Pipeline()
    
    # Stage 1: 爬取（必选）
    pipeline.add(ScraperProcessor(config))
    
    # Stage 2: 上传图片（可选）
    if not config.get('skip_images', False):
        pipeline.add(ImageUploaderProcessor(config))
    
    # 🔮 Stage 3: LLM（未来启用）
    llm_cfg = config.get('llm', {})
    if llm_cfg.get('enabled', False):
        logger.info("[Orchestrator] 启用 LLM 处理")
        pipeline.add(LLMProcessor(config))
    
    logger.info(f"[Orchestrator] Pipeline 构建完成: {len(pipeline.processors)} 个 Stage")
    return pipeline


def run_pipeline(config: dict) -> List[ProductItem]:
    """便捷函数：构建 pipeline 并执行"""
    pipeline = build_pipeline(config)
    items = pipeline.run([])  # ScraperProcessor 不依赖输入
    return items
```

### 4.6 `db/importer.py`（约 200 行）

```python
"""
数据库导入器 — 从 ProductItem 列表写入 t_product
"""
import logging
from datetime import datetime
from typing import List

import pymysql

from pipeline.base import ProductItem

logger = logging.getLogger('crawler')


class DatabaseClient:
    """MySQL 导入客户端"""
    
    def __init__(self, config: dict):
        db_cfg = config['database']
        self.config = {
            'host': db_cfg['host'],
            'port': db_cfg.get('port', 3306),
            'user': db_cfg['user'],
            'password': db_cfg['password'],
            'database': db_cfg['database'],
            'charset': 'utf8mb4'
        }
    
    def insert_products(self, products: List[ProductItem]) -> tuple:
        """批量 INSERT，返回 (成功数, 失败数)"""
        conn = pymysql.connect(**self.config)
        success = 0
        failed = 0
        try:
            with conn.cursor() as cur:
                for p in products:
                    try:
                        cur.execute(
                            "INSERT INTO t_product (fid, category_id, name, cover, description, norm_price, real_price, stock, sales, status, create_time, update_time) "
                            "VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)",
                            (
                                p.fid, p.category_id, p.name, p.minio_cover,
                                p.description, p.norm_price, p.real_price,
                                p.stock, p.sales, p.status,
                                datetime.now(), datetime.now()
                            )
                        )
                        success += 1
                    except Exception as e:
                        logger.warning(f"插入失败 {p.name}: {e}")
                        failed += 1
                conn.commit()
        finally:
            conn.close()
        return success, failed
    
    def generate_sql(self, products: List[ProductItem]) -> str:
        """生成可审计的 SQL 文件"""
        now = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        lines = [
            "-- 菜品数据导入 SQL",
            f"-- 生成时间: {now}",
            f"-- 记录数: {len(products)}",
            "",
            "SET NAMES utf8mb4;",
            "INSERT INTO t_product (...) VALUES"
        ]
        # ... 完整实现
        return "\n".join(lines)
```

### 4.7 `crawler.py`（瘦身后，约 150 行）

```python
# -*- coding: utf-8 -*-
"""
Crawler CLI 入口 — 组装 Pipeline 并执行输出
"""
import argparse
import json
import logging
import sys
from datetime import datetime
from pathlib import Path

import yaml

from pipeline.orchestrator import build_pipeline
from pipeline.base import ProductItem
from db.importer import DatabaseClient

logger = logging.getLogger('crawler')


def load_config() -> dict:
    """加载 YAML 配置"""
    # ... 同原实现
    pass


def main():
    parser = argparse.ArgumentParser(description='菜品数据爬虫')
    parser.add_argument('--categories', nargs='+', help='指定分类（可选）')
    parser.add_argument('--limit', type=int, default=50)
    parser.add_argument('--import', dest='do_import', action='store_true')
    parser.add_argument('--output', choices=['json', 'sql'], default='json')
    parser.add_argument('--dry-run', action='store_true')
    parser.add_argument('--skip-images', action='store_true')
    parser.add_argument('--yes', action='store_true')
    
    args = parser.parse_args()
    
    config = load_config()
    config['limit'] = args.limit
    config['skip_images'] = args.skip_images
    
    # 1. 构建并运行 Pipeline
    items = build_pipeline(config).run([])
    
    if not items:
        logger.error("Pipeline 未产出任何数据")
        sys.exit(1)
    
    # 2. Dry-run
    if args.dry_run:
        logger.info(f"=== DRY RUN（{len(items)} 条）===")
        for i in items[:5]:
            print(f"  - {i.name} ({i.category_name}) - ¥{i.real_price}")
        return
    
    # 3. 输出
    if args.output == 'sql' or args.do_import:
        db = DatabaseClient(config)
        if args.do_import:
            if not args.yes:
                confirm = input(f"将写入 {len(items)} 条到 t_product。继续？(y/n): ")
                if confirm.lower() != 'y':
                    logger.info("已取消")
                    return
            success, failed = db.insert_products(items)
            logger.info(f"导入完成: 成功 {success} / 失败 {failed}")
        else:
            sql = db.generate_sql(items)
            print(sql)
    else:
        # JSON 输出
        output_file = Path(__file__).parent / "output" / f"products_{datetime.now():%Y%m%d_%H%M%S}.json"
        output_file.parent.mkdir(exist_ok=True)
        # 转 dict
        data = [vars(i) for i in items]
        for d in data:
            d.pop('cover_url', None)
        with open(output_file, 'w', encoding='utf-8') as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
        logger.info(f"JSON 已保存: {output_file}")


if __name__ == '__main__':
    main()
```

---

## 五、严格验收 Checklist

我会逐项打勾，每项必须 ✅：

### 5.1 文件结构

- [ ] `pipeline/__init__.py` 存在
- [ ] `pipeline/base.py` 含 `Processor` 抽象类
- [ ] `pipeline/base.py` 含 `Pipeline` 编排类
- [ ] `pipeline/base.py` 含 `ProductItem` 数据类
- [ ] `pipeline/scraper.py` 含 `ScraperProcessor` 类
- [ ] `pipeline/image_uploader.py` 含 `ImageUploaderProcessor` 类
- [ ] `pipeline/llm_processor.py` 含 `LLMProcessor` 类（骨架）
- [ ] `pipeline/orchestrator.py` 含 `build_pipeline()` 函数
- [ ] `db/__init__.py` 存在
- [ ] `db/importer.py` 含 `DatabaseClient` 类
- [ ] `crawler.py` 瘦身为 CLI 入口（< 200 行）

### 5.2 架构验证

```bash
cd E:\Idea_project\delivery-cloud\tools\crawler

# 验收 1：检查 Processor 抽象
grep -A 3 "class Processor" pipeline/base.py
# 预期：含 @abstractmethod 和 process() 方法

# 验收 2：检查 Pipeline 编排
grep -A 5 "class Pipeline" pipeline/base.py
# 预期：含 add() 和 run() 方法

# 验收 3：检查 LLM 扩展点
grep "NotImplementedError" pipeline/llm_processor.py
# 预期：能找到

# 验收 4：检查 orchestrator 组装
grep "build_pipeline" pipeline/orchestrator.py
# 预期：能找到，且包含 LLMProcessor 的注释引用
```

### 5.3 功能回归（必须全部跑通）

- [ ] `python crawler.py --help` 显示帮助
- [ ] `python crawler.py --limit 5 --dry-run` 跑通（不写任何文件）
- [ ] `python crawler.py --limit 10` 输出 JSON 文件到 output/
- [ ] `python crawler.py --limit 10 --output sql` 输出 SQL 到 stdout
- [ ] `python crawler.py --limit 10 --skip-images` 跳过 MinIO 上传
- [ ] 数据库 INSERT 仍正常工作（用真实 db 跑 5 条验证）

### 5.4 LLM 扩展性验证（关键！）

```bash
# 验收 LLM 关闭时不报错
python crawler.py --limit 3 --dry-run
# 预期：正常完成

# 验收 LLM 开启时给出明确提示
# 临时编辑 config.yaml 设置 llm.enabled=true
python crawler.py --limit 3 --dry-run
# 预期：NotImplementedError，且提示信息清晰
```

---

## 六、风险与降级

| 风险 | 缓解 |
|------|------|
| 重构破坏现有功能 | 严格按 5.3 跑回归 |
| 数据库连接信息不一致 | 用原 config.yaml 不变 |
| 工程师没理解 Pipeline 价值 | 提供完整代码骨架（§四）|

---

## 七、签收

| 角色 | 操作 | 时间 |
|------|------|------|
| 工单创建 | Codex | 2026-09-05 |
| Python 执行 | _待填_ | |
| 重构完成 | _待填_ | |
| Codex 验收 | _待填_ | |

---

## 八、交付模板

完成后请提供：

```markdown
### [重构 R1] 交付报告

**完成时间**：2026-09-XX HH:MM
**实际工时**：X 分钟
**改动行数**：+/- X 行

**已完成 Checklist**：
- [x] 文件结构
- [x] 架构验证
- [x] 功能回归
- [x] LLM 扩展性

**运行截图 / 日志**：
（粘贴 dry-run 输出）

**遇到的问题**：
（如有）
```