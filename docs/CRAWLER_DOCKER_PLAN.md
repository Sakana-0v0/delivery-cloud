# 🐳 Docker 爬虫服务 - 实施方案规划

> **创建时间**：2026-09-02  
> **架构目标**：管理员可通过 Web 界面手动触发爬虫任务，支持定时任务，数据经 LLM 二次处理后入库

---

## 🏗️ 整体架构

`
┌─────────────────────────────────────────────────────────────────┐
│                        管理员前端 (Vue 3)                          │
│   [数据采集] 页面：触发按钮 | 任务列表 | 实时日志 | 导入数据库按钮    │
└──────────────────────────┬──────────────────────────────────────┘
                           │ HTTP / WebSocket
                           ↓
┌─────────────────────────────────────────────────────────────────┐
│                     Java 后端 (Spring Boot)                       │
│                                                                   │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │ 爬虫 Job API  │  │ Job 状态管理  │  │ 定时任务触发  │          │
│  │ (Controller)  │  │ (Service)    │  │ (Scheduler)  │          │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘          │
│         │                  │                  │                   │
│         └──────────────────┴──────────────────┘                   │
│                            ↓                                      │
│                    RabbitMQ Exchange                             │
│                    user.crawler.exchange                          │
└──────────────────────────┬──────────────────────────────────────┘
                           │ MQ 消息
                           ↓
┌─────────────────────────────────────────────────────────────────┐
│                   Python 爬虫服务 (Docker)                        │
│                                                                   │
│  FastAPI HTTP Server (端口 8001)                                 │
│                                                                   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  Consumer: 监听爬虫消息队列                                │   │
│  │      ↓                                                    │   │
│  │  1. 爬取 TheMealDB 原始数据                                │   │
│  │      ↓                                                    │   │
│  │  2. 下载图片 → 上传 MinIO                                  │   │
│  │      ↓                                                    │   │
│  │  3. 调用 LLM API 二次处理（清洗/翻译/生成描述）              │   │
│  │      ↓                                                    │   │
│  │  4. 生成 products_import.sql                              │   │
│  │      ↓                                                    │   │
│  │  5. 更新 Job 状态为「待审核」                              │   │
│  │      ↓                                                    │   │
│  │  6. 上传 SQL 文件到共享存储 / MinIO                        │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  REST API (供 Java 调用)                                   │   │
│  │  GET  /api/jobs/{id}/status     → Job 状态 + 日志         │   │
│  │  POST /api/jobs/{id}/approve    → 触发 SQL导入数据库      │   │
│  │  POST /api/jobs/{id}/reject     → 拒绝，清除临时文件       │   │
│  │  GET  /api/jobs/{id}/sql-file   → 下载生成的 SQL 文件     │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
                           ↓
                    生成的 SQL 文件
                           ↓
┌─────────────────────────────────────────────────────────────────┐
│                        MySQL 数据库                              │
│                  del_product_db.t_product                        │
└─────────────────────────────────────────────────────────────────┘
`

---

## 📦 交付物清单

### 1. Python 爬虫服务（Python 工程师）

| 文件 | 说明 |
|------|------|
| crawler_service/Dockerfile | Docker 镜像构建文件 |
| crawler_service/docker-compose.yml | 容器编排配置 |
| crawler_service/app/main.py | FastAPI 入口 + HTTP 接口 |
| crawler_service/app/consumer.py | RabbitMQ 消费者 |
| crawler_service/app/crawler/ | 爬虫核心逻辑（复用现有脚本） |
| crawler_service/app/llm_client.py | LLM API 集成（后续接入） |
| crawler_service/requirements.txt | Python 依赖 |
| crawler_service/.env.example | 环境变量模板 |

### 2. Java 后端改造（Java 工程师）

| 文件 | 说明 |
|------|------|
| CrawlerJobController.java | 管理员爬虫相关 API |
| CrawlerJobService.java | Job 创建 / 状态查询 |
| RabbitMQConfig.java | 新增爬虫 Exchange / Queue 配置 |
| CrawlerScheduler.java | 定时任务触发（每天凌晨） |
| 	_crawler_job 表 | Job 状态记录表 |

### 3. 前端管理员界面（前端工程师）

| 文件 | 说明 |
|------|------|
| iews/admin/CrawlerView.vue | 数据采集管理页面 |
| pi/admin/crawler.ts | 爬虫相关 API 封装 |

### 4. 运维（运维工程师）

| 文件 | 说明 |
|------|------|
| 服务器 Docker / Docker Compose 安装 | 宿主机环境准备 |
| docker-compose.prod.yml | 生产环境编排 |
| Nginx 反向代理配置 | Python 服务端口 8001 → 内部路由 |

---

## 🔧 技术栈

| 组件 | 技术 |
|------|------|
| Python 服务框架 | FastAPI |
| Python 异步队列 | aiohttp / httpx |
| 消息队列 | RabbitMQ（复用现有） |
| 容器化 | Docker + Docker Compose |
| LLM 集成 | OpenAI API / 国产模型（待定） |
| Java 框架 | Spring Boot（现有） |
| 前端 | Vue 3（现有）|

---

## 📋 任务分工

### 🐍 Python 工程师

`
职责：开发 Python 爬虫服务（Docker 化）

1. 编写 Dockerfile，基于 Python 3.11 镜像
2. 编写 docker-compose.yml，与现有服务同一网络
3. 实现 FastAPI HTTP 接口：
   - GET  /status/{job_id}     → 返回 Job 状态和日志
   - POST /trigger             → 手动触发爬虫（MQ 消息）
   - POST /approve/{job_id}    → 触发 SQL 入库
   - POST /reject/{job_id}     → 拒绝，清除临时文件
4. 实现 RabbitMQ Consumer，监听爬虫消息
5. 爬虫核心逻辑可复用现有脚本（crawl_meal.py 等）
6. LLM 二次处理模块（预留接口，当前可先跳过）
7. 输出：生成的 SQL 文件路径或 MinIO URL
`

### ☕ Java 工程师

`
职责：后端 Job 管理 + 定时任务 + 前端 API 适配

1. 新建数据库表 t_crawler_job
   - id, job_id(UUID), status(PENDING/RUNNING/LLM_PROCESSING/PENDING_APPROVE/APPROVED/REJECTED/FAILED)
   - total_count, success_count, fail_count
   - sql_file_url, created_at, updated_at, created_by

2. 新建 CrawlerJobController（Admin 端）
   - POST /admin/crawler/jobs          → 手动触发爬虫
   - GET  /admin/crawler/jobs          → 任务列表（分页）
   - GET  /admin/crawler/jobs/{id}    → 任务详情
   - POST /admin/crawler/jobs/{id}/approve   → 确认导入
   - POST /admin/crawler/jobs/{id}/reject    → 拒绝
   - GET  /admin/crawler/jobs/{id}/log       → 实时日志

3. 实现定时任务（CrawlerScheduler）
   - 每天凌晨 3:00 自动触发爬虫
   - Cron 表达式可配置

4. RabbitMQ 配置
   - 新增 Exchange: user.crawler.exchange（topic）
   - 新增 Queue: user.crawler.queue
   - Routing Key: crawler.trigger

5. Job 状态同步
   - 定时轮询 Python 服务的 /status/{job_id} 接口
   - 或 Python 服务回调 Java 接口更新状态
`

### 🎨 前端工程师

`
职责：管理员界面 - 数据采集模块

1. 新增路由 /admin/crawler → CrawlerView

2. CrawlerView.vue 功能：
   - 任务列表（表格 + 分页）
   - 状态标签：待审核 / 运行中 / 已完成 / 已拒绝
   - 触发按钮：手动执行爬虫（带确认弹窗）
   - 任务详情：
     * 基本信息（条数 / 耗时 / 创建时间）
     * 日志输出（滚动实时）
     * 预览生成的 SQL（可选）
   - 操作按钮：
     * 「导入数据库」→ 确认后调用 /admin/crawler/jobs/{id}/approve
     * 「拒绝」→ 调用 /admin/crawler/jobs/{id}/reject
     * 「下载 SQL」→ 下载生成的 SQL 文件

3. 状态轮询：
   - 运行中的任务每 5s 轮询一次状态
   - 日志滚动加载
`

### 🛠️ 运维工程师

`
职责：服务器环境准备 + Docker 部署

1. 服务器安装 Docker 和 Docker Compose
2. 配置 Docker 网络（与 RabbitMQ / MySQL / MinIO 同一网络）
3. 配置 Nginx 反向代理（可选）
4. 编写部署脚本或 Ansible Playbook
5. 配置日志收集（可选：ELK / Loki）
`

---

## 📅 实施优先级

| 阶段 | 内容 | 负责 | 优先级 |
|------|------|------|--------|
| **Phase 1** | Python 服务 Docker 化（不含 LLM） | Python | P0 |
| **Phase 1** | Java Job API + 定时任务 | Java | P0 |
| **Phase 1** | 管理员 CrawlerView 页面 | 前端 | P0 |
| **Phase 2** | LLM 二次处理集成 | Python | P1 |
| **Phase 2** | 实时日志 WebSocket 推送 | Python + 前端 | P1 |
| **Phase 3** | 生产环境部署脚本 | 运维 | P2 |

---

## ⚠️ 注意事项

1. **网络互通**：Python Docker 容器需与宿主机的 RabbitMQ / MySQL / MinIO 网络互通
2. **Python 依赖**：requirements.txt 需包含 fastapi, uvicorn, aiohttp, httpx, pika, minio 等
3. **共享存储**：生成的 SQL 文件需放到宿主机或 MinIO，供 Java 下载执行
4. **安全**：LLM API Key 通过环境变量注入，不写在代码里
5. **幂等**：重复触发同一任务应被拒绝或创建新任务

---

*规划完成，待评审后分派给各工程师执行*
