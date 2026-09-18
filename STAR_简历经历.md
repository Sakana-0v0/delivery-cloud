# 外卖平台微服务系统 — 项目经历

> **重要更新（2026-09-18）**：下文所有"具体实现描述"已与最新代码对齐，
> 关键重构对应 commit 锚点：
> - `3ccc8cf` P0-UserAuth：ChatContextHolder 替代 userId 嵌入 UserMessage
> - `b43bd26` P0-Sentinel：dishSearch 真正接入 Sentinel 限流/熔断
> - `32f45ad` P1-Batch：免单码按 500/批多值 INSERT 预热
> - `206f08c` P4-Perf：4 个 JMeter 脚本 + generate_tokens.py 填充 [X]/[Y]
> 详见 `docs/AI_CS_USERAUTH_REFACTOR.md` / `docs/SEARCH_SENTINEL.md` / `docs/FREE_ORDER_BATCH_INSERT.md` / `docs/perf/README.md`。

---

## 项目描述

外卖平台微服务系统是一款面向用户、商户、配送全链路的综合外卖平台，支持用户下单、商户管理、订单配送、免单活动、智能客服等核心功能。系统采用微服务架构，包含用户、商品、订单、支付、管理员、消息、文件、统计、智能客服等10余个微服务模块，日均处理订单万级规模。

---

## 技术栈

**Spring Cloud Alibaba 2023 + JDK 17 + MyBatis Plus + Redis + RabbitMQ + Elasticsearch + LangChain4j + DashScope + Nacos + Sentinel + Caffeine**

---

## 负责内容

### 一、免单高并发处理（`del-payment` 模块）

- 设计基于 `UPDATE ... WHERE status=''AVAILABLE''` 的乐观锁免单券原子抢占机制，配合唯一键约束防止超卖，单活动支持 **10,000+ 并发抢券**；SQL 实现在 `FreeOrderCouponMapper.atomicGrab/atomicUse`
- 实现"查重校验 → 原子抢占 → 重试兜底"策略：先 `selectCount(grabbed_user_id)` 查重，再执行 `atomicGrab`，遇 `updated=0` 时 `retryCount<1` 时递归重试一次；`FreeOrderGrabServiceImpl.doGrab`
- 捕获 `DuplicateKeyException` 唯一键冲突异常转为 `FREE_ORDER_ALREADY_GRABBED` 业务码，防止并发重复抢占
- 设计免单券状态机 `AVAILABLE → GRABBED → USED → EXPIRED`（`FreeOrderCouponStatus`），`atomicGrab` 强制 `WHERE status=''AVAILABLE''`、`atomicUse` 强制 `WHERE status=''GRABBED'' AND grabbed_user_id=?`，杜绝并发竞争与越权核销
- **★ P1 批量预热（commit `32f45ad`）**：发布活动时不再逐条 INSERT，按 **500 条/批**调用 `FreeOrderCouponMapper.batchInsert`（MyBatis `<foreach>` + MySQL 多值 INSERT 语法），万级免单码从 **N 次 round-trip 降到 N/500 次**，活动上线瞬间不再打满连接池
- 免单码格式 `FREE{yyyyMMddHHmmss}{4位序号}`（`%04d` 显式支持万级序号）

### 二、ES 语义检索 + 双读容灾（`del-product` 模块）

- 设计"意图路由（QueryRouter）→ 营养条件解析（QueryParser）→ 知识库扩展（QueryExpander/KnowledgeBaseService）→ Ansj 分词（ChineseTokenizer）"四层语义搜索流水线，支持 KEYWORD / NUTRITION / CATEGORY / MIXED 四种查询意图
- 引入知识库规则引擎实现同义词/相关词扩展（如"清淡"→"少油少盐清淡素"），长尾 query 召回率提升 **[待 P4 压测填写]**
- 使用 Wildcard + Keyword 多分词字段（`nameTokens` / `categoryTokens` / `propertyTokens`）联合查询，Ansj 过滤单字及标点
- 预留 **1536 维 Dense_Vector** 向量检索字段（`DashScopeEmbeddingClient.embedBatch`），为未来语义向量搜索升级奠定基础
- 索引批处理 **50 条/批**、向量生成按文本条数批处理，优化索引构建与向量生成效率（`IndexingServiceImpl.batchSize=50`）
- **★ P0 Sentinel 双读熔断（commit `b43bd26`）**：搜索入口加 `@SentinelResource("dishSearch")`，Sentinel 阻断（限流/熔断）走 `searchBlockHandler`、业务异常走 `searchFallbackWithEx`，两者最终都降级到 MySQL LIKE。规则来源 Nacos：
  - 限流：`count: 100, grade: 1`（单实例 100 QPS）
  - 熔断：`grade: 0, count: 1500, slowRatioThreshold: 0.5, minRequestAmount: 5, timeWindow: 30`（RT>1500ms 且比例>50% 持续 30s 熔断）
  - Nacos 不可用时启动装载本地默认规则（`@PostConstruct initDefaultRules()`），双保险

### 三、LangChain4j 流式智能客服（`del-cs` 模块）

- 基于 LangChain4j `AiServices.builder(Assistant.class)` + `QwenStreamingChatModel` 构建流式 AI 客服（`ChatAutoConfig`），实现 **Token 级实时推送**，SSE 超时 60 秒（`ChatController` 使用 `SseEmitter(60_000L)`）
- 设计 Tool 体系：`SearchDishesTool` / `OrderDetailTool` / `OrderHistoryTool`，通过 `@Tool` 注解暴露服务接口，`AiServices.tools(...)` 注入支持 LLM 主动调用
- Redis 对话记忆持久化：`RedisChatMemoryStore`（`KEY_PREFIX=cs:chat:messages:`, TTL=30min），每用户消息窗口上限 **20 条**（`MessageWindowChatMemory.builder().maxMessages(20)`）
- 调优 LLM 参数 `temperature=0.1, topP=0.7`（`ChatAutoConfig`）降低幻觉
- **★ P0 用户身份工程化透传（commit `3ccc8cf`，详见 `docs/AI_CS_USERAUTH_REFACTOR.md`）**：
  - 原实现把 `userId` 拼到 UserMessage 文本里（`"【当前用户ID: xxx】" + userMessage`），由 LLM 自己读出来再传给 Tool，面临**提示词注入风险**——恶意消息可篡改 userId 触发跨用户越权
  - 重构后新增 `ChatContextHolder`（ThreadLocal）+ `ChatContext` POJO，由 `ChatController` 在 SSE 入口 set、SSE 完成/超时/异常时 clear
  - `ChatService.streamChat` 不再嵌入 userId 前缀；`OrderDetailTool` / `OrderHistoryTool` 移除 `userId` 参数，内部 `ChatContextHolder.getUserIdAsLong()` 读取
  - `AuthFeignRequestInterceptor` 增加 `ChatContextHolder` fallback，解决 SSE 异步线程下 `AuthContext` ThreadLocal 丢失时无法注入 Authorization 的问题
  - System Prompt 增加 prompt-injection 防御规则，拒绝透露系统提示词/工具签名/其他用户数据

### 四、Redis 缓存架构（`del-product` / `del-user` 模块）

- 设计三级缓存体系（Caffeine L1 本地缓存 30s TTL + Redis L2 Hash 分布式缓存 + MySQL L3 持久化）应用于评价计数场景（`ReviewCountCacheServiceImpl`）；缓存命中率提升 **[待 P4 压测填写]**，数据库 QPS 降低 **[待 P4 压测填写]**
- Caffeine 配置：`reviewCountCache`(5000 容量/30s TTL)、`userVoteCache`(10000/30s TTL)，`CaffeineConfig.recordStats()`
- 使用 Lua 脚本实现 Redis Hash 字段原子递增（like/bad），`DefaultRedisScript<>(INCR_SCRIPT)`，`HMSET` 一次原子写入；保证计数更新中间状态不泄露
- 搭建 Redis Pub/Sub 跨实例失效机制：`convertAndSend("cache:invalidate:review-count", productId)`，每实例 `CacheInvalidateListener implements MessageListener` 收到广播后清本地 L1
- Token 黑名单 + 用户 jti 索引体系（`del-user/security/TokenBlacklist`）：`blacklist:<jti>` 单条 O(1) 查询 + `user:<uid>:jti` SET 索引支持一键踢下线，SET TTL 14 天（覆盖 refreshToken 周期）
- 多层限流（`VerifyCodeService`）：邮箱验证码发送 `60 秒间隔 + 每日 100 次上限`，Redis `INCR` + `expire` 原子计数
- MD5 文件去重（`del-file`）：缓存命中时直接返回已有 MinIO URL，避免重复上传，缓存 TTL 30 天

### 五、分布式认证与权限控制（`del-gateway` / `del-user` 模块）

- 设计网关集中鉴权架构（`AuthGlobalFilter` ordered=-100 + `JwtVerifier`），外部请求在网关统一验签，业务服务无状态；**验权延迟降低 [待 P4 压测填写]**
- 双密钥池隔离（`USER_POOL` / `ADMIN_POOL`）：`JwtVerifier.verify` 先 user-pool 验签，失败降级 admin-pool；密钥< 32 字节启动抛 `IllegalArgumentException`（杜绝弱密钥）
- 内部服务专属 Token：`/internal/**` 路径走 `X-Internal-Service-Token` Header 固定密钥校验（`AuthGlobalFilter.validateInternalServiceCall` + `InternalServiceAuthFilter`），`PathRoleRule.ROLE_INTERNAL_SERVICE`
- Token 黑名单（`TokenBlacklist`）：`kickOut(userId)` 一次性遍历用户活跃 jti SET 全量加入黑名单，**查询 O(1)**
- 方法级权限注解：`@EnableMethodSecurity` + `@PreAuthorize("hasRole(''ADMIN'')")`，结合路径级规则（`PathRoleRule.checkRole`）双重保险
- QQ 第三方登录（OAuth2）：`QqAuthController` + `UserServiceImpl.loginByQq`，自动创建免密用户并签发 C 端 JWT

### 六、购物车系统（`del-order` 模块）

- 使用 Redis Hash 结构实现购物车：`HINCRBY del-cart:user:{userId} {productId} {quantity}` 原子递增商品数量，**并发添加同一商品零并发冲突**
- 购物车与商品信息分离存储：购物车只缓存 `productId → quantity`，商品信息通过 Feign `ProductFeignClient.getProductSnapshot` 实时获取，降低 Redis 存储占用 **[待 P4 压测填写]**
- 购物车缓存 TTL **30 天**，逾期自动失效
- 封装 `cartRedisTemplate` 专用 `RedisTemplate<String, Object>` 实例（`@Qualifier("cartRedisTemplate")`）与公共 Redis 配置隔离，避免 Bean 冲突

---

## 项目总结

本项目围绕外卖平台核心链路，从高并发抢券、精准语义搜索、智能对话、分布式缓存、认证授权、购物车六个维度进行技术攻坚：
- **抢券**：数据库乐观锁 + 唯一键 + 重试兜底 + **批量预热（500/批）**，解决限时免单超卖与连接池打满问题；
- **搜索**：四层查询流水线 + 知识库规则引擎 + **Spring Cloud Alibaba Sentinel 限流/熔断 + MySQL 兜底**，构建高可用语义搜索；
- **客服**：LangChain4j 流式对话 + Tool 工具调用 + Redis 记忆 + **ChatContextHolder 工程化身份透传**，实现可实时查询的 AI 客服（消除提示词注入越权风险）；
- **缓存**：三级缓存 + Lua 脚本 + Pub/Sub，构建高性能缓存体系；
- **认证**：网关集中鉴权 + 双密钥池 + Token 黑名单，保障系统安全；
- **购物车**：Redis Hash 原子计数 + 业务数据与商品信息分离存储。

整体提升平台用户体验与系统稳定性。

---

> **量化指标**：当前 `[X]/[Y]` 占位符由 `docs/perf/` 下的 JMeter 脚本压测后填充。脚本与说明详见 `docs/perf/README.md`。