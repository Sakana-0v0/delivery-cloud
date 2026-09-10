# 🐳 工单 #CRAWLER-001：Docker 爬虫服务实施

> **创建时间**：2026-09-04
> **状态**：⏸️ **封档（2026-09-04）** — 业务优先级调整，暂停开发
> **优先级**：P0（业务需求）
> **接收方**：Python 工程师 + Java 工程师 + 前端工程师 + 运维工程师
> **预计工时**：4-5 天
> **架构选型**：Python + Docker + FastAPI（基于已确认决策）

---

## ⚠️ 封档说明

本工单已封档，不实施。

**封档原因**：业务优先级调整
**重启条件**：见 `docs/TODO.md`

**封档时的关键决策**（避免重启时重新讨论）：

1. **状态同步**：Python 回调 Java REST（Python 不直连 MySQL）
2. **审核工作流**：JSON 数据预览（不看 SQL）
3. **LLM**：先不集成，但 Pipeline 抽象层已设计好
4. **Java 端归属**：del-admin 服务（不加第 10 个服务）
5. **数据源**：TheMealDB（公开 API 免费）
6. **调度时间**：每天 8:00 / 14:00 / 20:00

---

(以下为原始工单内容，仅供历史参考，不作为实施依据)

## 一、架构总览

```
[Vue CrawlerView] → [del-admin API] → [RabbitMQ] → [Python Crawler]
                                                            ↓
                                                    [MinIO + TheMealDB]
                                                            ↓
                                          [callback → del-admin 更新状态]
                                                            ↓
                                              [管理员预览 JSON → 确认导入]
                                                            ↓
                                              [del-admin 写入 t_product]
```

## 二、任务分派

### 🐍 Python 工程师（1.5 天）

| 文件 | 操作 | 说明 |
|------|------|------|
| `python-crawler/Dockerfile` | 新建 | Python 3.11-slim 镜像 |
| `python-crawler/requirements.txt` | 新建 | fastapi / httpx / aio-pika / minio |
| `python-crawler/docker-compose.yml` | 新建 | 与现有服务同网络 |
| `app/main.py` | 新建 | FastAPI 入口 |
| `app/core/config.py` | 新建 | 环境变量配置 |
| `app/core/mq_consumer.py` | 新建 | RabbitMQ 消费者 |
| `app/core/http_client.py` | 新建 | 调用 Java REST |
| `app/pipeline/base.py` | **新建** | 🔮 抽象 Processor（LLM 扩展点）|
| `app/pipeline/orchestrator.py` | 新建 | Pipeline 编排器 |
| `app/pipeline/scraper.py` | 新建 | TheMealDB 爬取 |
| `app/pipeline/image_uploader.py` | 新建 | 图片 → MinIO |
| `app/services/crawler.py` | 新建 | 主业务流程 |

### ☕ Java 工程师（2 天）

| 文件 | 操作 | 说明 |
|------|------|------|
| `del-admin/.../crawler/entity/CrawlerJob.java` | 新建 | 任务实体 |
| `del-admin/.../crawler/entity/CrawlerJobItem.java` | 新建 | 任务项实体 |
| `del-admin/.../crawler/dao/CrawlerJobRepository.java` | 新建 | Repository |
| `del-admin/.../crawler/dto/TriggerReq.java` | 新建 | 触发请求 |
| `del-admin/.../crawler/dto/JobDTO.java` | 新建 | 任务 DTO |
| `del-admin/.../crawler/dto/ItemDTO.java` | 新建 | 项 DTO |
| `del-admin/.../crawler/service/CrawlerJobService.java` | 新建 | 业务逻辑 + 状态机 |
| `del-admin/.../crawler/web/CrawlerJobController.java` | 新建 | 7 个管理 API |
| `del-admin/.../crawler/web/CrawlerInternalController.java` | 新建 | 2 个回调 API |
| `del-admin/.../crawler/job/CrawlerScheduler.java` | 新建 | 每天 8/14/20 点 |
| `del-admin/.../crawler/config/CrawlerMQConfig.java` | 新建 | Exchange / Queue 配置 |
| `del-admin/src/main/resources/db/migration/V{timestamp}__create_crawler_tables.sql` | 新建 | 建表 SQL |

### 🎨 前端工程师（1 天）

| 文件 | 操作 | 说明 |
|------|------|------|
| `src/views/admin/CrawlerView.vue` | 新建 | 主页面 |
| `src/api/admin/crawler.ts` | 新建 | API 封装 |
| `src/router/index.ts` | 修改 | 新增路由 |
| `src/views/admin/AdminLayout.vue` | 修改 | 侧边栏菜单 |

### 🛠️ 运维工程师（0.5 天）

| 文件 | 操作 | 说明 |
|------|------|------|
| 服务器 Docker / Compose 安装 | - | 宿主机环境 |
| Nginx 配置 | 新建 | Python 8001 反向代理（如需要）|
| 部署脚本 | 新建 | `scripts/deploy-crawler.sh` |

---

## 三、数据库设计（Java 工程师）

### 3.1 SQL 脚本

**文件**：`del-admin/src/main/resources/db/migration/V20260904__create_crawler_tables.sql`

```sql
CREATE TABLE IF NOT EXISTS `t_crawler_job` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `job_id`        VARCHAR(64)   NOT NULL                COMMENT 'UUID',
    `status`        VARCHAR(32)   NOT NULL                COMMENT 'PENDING/RUNNING/PREVIEW_READY/APPROVED/IMPORTING/COMPLETED/FAILED/REJECTED',
    `trigger_type`  VARCHAR(16)   NOT NULL                COMMENT 'MANUAL/SCHEDULED',
    `triggered_by`  VARCHAR(64)                          COMMENT 'admin user id or system',
    `source`        VARCHAR(32)                          COMMENT 'TheMealDB etc',
    `config_json`   JSON                                COMMENT '爬虫配置',
    `total_count`   INT           DEFAULT 0,
    `success_count` INT           DEFAULT 0,
    `fail_count`    INT           DEFAULT 0,
    `started_at`    DATETIME(3),
    `finished_at`   DATETIME(3),
    `error_message` TEXT,
    `created_at`    DATETIME(3)   DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`    DATETIME(3)   DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_job_id` (`job_id`),
    KEY `idx_status` (`status`),
    KEY `idx_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='爬虫任务';

CREATE TABLE IF NOT EXISTS `t_crawler_job_item` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT,
    `job_id`        VARCHAR(64)   NOT NULL                COMMENT '关联 t_crawler_job.job_id',
    `source_id`     VARCHAR(128)                         COMMENT '源系统 ID',
    `name`          VARCHAR(255),
    `description`   TEXT,
    `category`      VARCHAR(64),
    `cover_url`     VARCHAR(512),
    `price`         DECIMAL(10,2),
    `raw_data`      JSON                                COMMENT '原始数据，便于 LLM 改造',
    `status`        VARCHAR(32)   DEFAULT 'PENDING'      COMMENT 'PENDING/IMPORTED/FAILED/SKIPPED',
    `error_message` TEXT,
    `created_at`    DATETIME(3)   DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_job_source` (`job_id`, `source_id`),
    KEY `idx_job` (`job_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='爬虫任务项预览';
```

### 3.2 状态机

```
PENDING → RUNNING → PREVIEW_READY → APPROVED → IMPORTING → COMPLETED
   │         │            │            │
   ↓         ↓            ↓            ↓
  取消    失败 FAILED   拒绝 REJECTED  失败 FAILED
```

未来加 LLM_PROCESSING 时插入 RUNNING → LLM_PROCESSING → PREVIEW_READY。

---

## 四、Java API 设计（Java 工程师）

### 4.1 管理端 API（7 个）

```java
@RestController
@RequestMapping("/api/v1/admin/crawler/jobs")
public class CrawlerJobController {

    @PostMapping
    public R<JobDTO> trigger(@RequestBody TriggerReq req);

    @GetMapping
    public R<PageResult<JobDTO>> list(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String status);

    @GetMapping("/{jobId}")
    public R<JobDTO> detail(@PathVariable String jobId);

    @GetMapping("/{jobId}/preview")
    public R<PreviewDTO> preview(
        @PathVariable String jobId,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size);

    @PostMapping("/{jobId}/approve")
    public R<Void> approve(@PathVariable String jobId);

    @PostMapping("/{jobId}/reject")
    public R<Void> reject(@PathVariable String jobId, @RequestBody RejectReq req);

    @GetMapping("/{jobId}/log")
    public R<LogDTO> getLog(@PathVariable String jobId);
}
```

### 4.2 内部回调 API（2 个，Python 调用）

```java
@RestController
@RequestMapping("/internal/crawler")
public class CrawlerInternalController {

    @PostMapping("/jobs/{jobId}/status")
    public R<Void> updateStatus(
        @PathVariable String jobId,
        @RequestBody StatusUpdateReq req);

    @PostMapping("/jobs/{jobId}/result")
    public R<Void> reportResult(
        @PathVariable String jobId,
        @RequestBody ResultReportReq req);
}
```

**安全**：内部 API 必须校验内部服务 Token（复用 `InternalServiceFeignInterceptor` 模式）。

### 4.3 MQ 消息结构

```java
public class CrawlerTriggerMessage {
    private String jobId;
    private String triggerType;     // MANUAL | SCHEDULED
    private String triggeredBy;
    private String source;
    private Map<String, Object> config;  // categories/maxItems/enableLlm
    private LocalDateTime timestamp;
}
```

### 4.4 Scheduler 配置

```java
@Component
@RequiredArgsConstructor
public class CrawlerScheduler {

    private final CrawlerJobService crawlerJobService;

    @Scheduled(cron = "0 0 8,14,20 * * ?")
    public void autoTrigger() {
        try {
            crawlerJobService.triggerScheduledJob(
                "system",
                JobConfig.defaultConfig()
            );
        } catch (Exception e) {
            log.error("[CrawlerScheduler] 自动触发失败", e);
        }
    }
}
```

### 4.5 幂等性保障

```java
@Service
public class CrawlerJobService {

    @Transactional
    public String triggerJob(Long adminUserId, TriggerReq req) {
        long runningCount = jobRepository.countByStatusIn(
            List.of("PENDING", "RUNNING")
        );
        if (runningCount > 0) {
            throw new BizException("已有爬虫任务正在运行，请稍后再试");
        }

        String jobId = UUID.randomUUID().toString();
        CrawlerJob job = new CrawlerJob();
        job.setJobId(jobId);
        job.setStatus("PENDING");
        job.setTriggerType("MANUAL");
        job.setTriggeredBy(String.valueOf(adminUserId));
        job.setSource(req.getSource());
        job.setConfigJson(JSON.toJSONString(req.getConfig()));
        jobRepository.insert(job);

        rabbitTemplate.convertAndSend(
            "user.crawler.exchange",
            "crawler.trigger",
            buildMessage(job)
        );

        return jobId;
    }
}
```

---

## 五、Python 服务设计（Python 工程师）

### 5.1 项目结构

```
python-crawler/
├── Dockerfile
├── requirements.txt
├── docker-compose.yml
├── app/
│   ├── main.py
│   ├── core/
│   │   ├── config.py
│   │   ├── mq_consumer.py
│   │   └── http_client.py
│   ├── pipeline/
│   │   ├── base.py             # 🔮 抽象 Processor
│   │   ├── orchestrator.py
│   │   ├── scraper.py
│   │   ├── image_uploader.py
│   │   └── llm_processor.py    # 🔮 预留 LLM
│   ├── services/
│   │   └── crawler.py
│   └── models/
│       └── product.py
└── scripts/
    └── entrypoint.sh
```

### 5.2 Pipeline 抽象（关键！为 LLM 扩展点）

```python
# pipeline/base.py
from abc import ABC, abstractmethod
from models.product import ProductItem

class Processor(ABC):
    """每个处理阶段都是独立 Processor，可自由组合"""
    
    @abstractmethod
    async def process(self, item: ProductItem) -> ProductItem:
        pass

# pipeline/orchestrator.py
class Pipeline:
    def __init__(self):
        self.processors: list[Processor] = []
    
    def add(self, processor: Processor) -> 'Pipeline':
        self.processors.append(processor)
        return self
    
    async def run(self, item: ProductItem) -> ProductItem:
        for p in self.processors:
            item = await p.process(item)
        return item

# services/crawler.py
def build_pipeline(config) -> Pipeline:
    pipeline = Pipeline()
    pipeline.add(ScraperProcessor())
    pipeline.add(ImageUploaderProcessor())
    
    # 未来启用 LLM 只需解开注释
    # if config.enable_llm:
    #     pipeline.add(LLMProcessor(api_key=...))
    
    return pipeline
```

### 5.3 关键代码片段

**ScraperProcessor**（TheMealDB）：
```python
class ScraperProcessor(Processor):
    BASE_URL = "https://www.themealdb.com/api/json/v1/1"
    
    async def process(self, item: ProductItem) -> ProductItem:
        async with httpx.AsyncClient() as client:
            ids = await self._fetch_by_category(client, item.category)
            for meal_id in ids[:item.max_items]:
                detail = await self._fetch_detail(client, meal_id)
                item.add_raw_meal(detail)
        return item
```

**JavaCallback**：
```python
async def report_result(self, job_id, total, success, fail, items):
    for i in range(0, len(items), 50):
        batch = items[i:i+50]
        async with httpx.AsyncClient() as client:
            await client.post(
                f"{self.base_url}/internal/crawler/jobs/{job_id}/result",
                headers={"X-Internal-Token": self.token},
                json={
                    "status": "PREVIEW_READY",
                    "totalCount": total,
                    "successCount": success,
                    "failCount": fail,
                    "items": batch
                }
            )
```

### 5.4 Dockerfile

```dockerfile
FROM python:3.11-slim
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY app/ ./app/
COPY scripts/entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh
EXPOSE 8001
ENTRYPOINT ["/entrypoint.sh"]
```

### 5.5 docker-compose.yml

```yaml
version: '3.8'
services:
  python-crawler:
    build: ./python-crawler
    container_name: python-crawler
    ports:
      - "8001:8001"
    environment:
      - RABBITMQ_HOST=host.docker.internal
      - JAVA_API_BASE=http://host.docker.internal:10010
      - INTERNAL_TOKEN=${INTERNAL_TOKEN}
      - MINIO_ENDPOINT=host.docker.internal:9000
      - MINIO_ACCESS_KEY=${MINIO_ACCESS_KEY}
      - MINIO_SECRET_KEY=${MINIO_SECRET_KEY}
      - MINIO_BUCKET=product-picture
      - LLM_ENABLED=false
    extra_hosts:
      - "host.docker.internal:host-gateway"
    networks:
      - delivery-net
networks:
  delivery-net:
    external: true
```

---

## 六、前端 CrawlerView 设计（前端工程师）

页面布局 + 关键交互同原方案（任务列表 / 详情 / 预览 / 确认导入 / 拒绝）。

---

## 七、运维部署（运维工程师）

| 资源 | 最低 |
|------|------|
| CPU | 2 核 |
| 内存 | 4GB |
| 磁盘 | 20GB |
| Docker | 20.10+ |

部署步骤见原方案。

---

## 八、测试与验收清单

- [ ] Python 服务 Docker 镜像构建成功
- [ ] Java 服务 7+2 API 全部通过
- [ ] 前端 CrawlerView 完整功能
- [ ] 端到端：手动触发 → 预览 → 确认导入
- [ ] 幂等性：重复触发被拒绝
- [ ] 状态机正确流转

---

## 九、附录：未来 LLM 扩展点

接入 LLM 只需：

1. 写 `LLMProcessor` 类（实现 `Processor.process()` 方法）
2. 在 `services/crawler.py` 的 `build_pipeline()` 加一行：

```python
def build_pipeline(config):
    pipeline = Pipeline()
    pipeline.add(ScraperProcessor())
    pipeline.add(ImageUploaderProcessor())
    
    if config.enable_llm:
        pipeline.add(LLMProcessor(
            api_key=settings.LLM_API_KEY,
            model=settings.LLM_MODEL,
            prompt_template=settings.LLM_PROMPT
        ))
    
    return pipeline
```

3. 数据库 `t_crawler_job_item.raw_data` 字段已留好 JSON
4. 状态机已预留 `LLM_PROCESSING` 状态位

**接入成本**：1 个 Python 文件 + 1 行配置，**不影响其他代码**。

---

## 十、签收

| 角色 | 操作 | 时间 |
|------|------|------|
| 工单创建 | Codex | 2026-09-04 |
| **状态** | ⏸️ **封档** | 2026-09-04 |

---

**重启本工单时，请先阅读 `docs/TODO.md` 中的封档决策，避免重新讨论已定事项。**