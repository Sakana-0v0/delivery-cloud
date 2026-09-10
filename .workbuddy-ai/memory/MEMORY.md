# delivery-cloud 项目长期记忆

## 项目定位
外卖/点餐微服务平台（Spring Cloud Alibaba 微服务，Java 17，Maven 多模块，groupId `com.sakana`）。
仓库只含后端；前端为独立仓库，联调通过网关 C 端 `/api/v1/*`、B 端 `/api/v1/admin/*`。

## 模块与真实端口（以各模块 application.yml 为准，文档有滞后！）
| 模块 | 端口 | 职责 |
|---|---|---|
| del-gateway | 10010 | 统一入口、JWT 鉴权、PathRoleRule 角色路由 |
| del-product | 10000 | 商品/分类/评价/点赞踩/搜索(ES+dashscope+ansj) |
| del-order | 10001 | 订单 + 购物车 |
| del-admin | 10002 | 管理后台 |
| del-user | 10003 | 用户/地址/QQ 登录 |
| del-payment | 10004 | 支付宝支付、退款 |
| del-message | 10005 | 邮件/站内通知（MQ 消费方） |
| del-file | 10006 | 文件上传（MinIO + Redis 去重） |
| del-stats | 10009 | 统计聚合（Feign 拉各服务） |
| del-cs | 10011 | LLM 智能客服（langchain4j + 通义千问，在建） |
| del-common | - | 公共 R/VO/工具/外部 RestTemplate |

## 基础设施约定
- Nacos: `127.0.0.1:8848`，namespace `sakana`，shared-configs 导入 `common-*.yml`
- 内部服务互调头：`X-Internal-Service-Token: internal-service-secret-key-2024`
- MQ: RabbitMQ，Outbox 模式（user_event_outbox / order_outbox / pay_outbox，状态 0 NEW→1 SENT→2 ACK→3 DLQ）
- 缓存 Caffeine + Redis；ORM MyBatis-Plus 3.5.7

## 工程纪律（项目强约定）
- 工单制：docs/WORK_ORDER_*.md（BUG-/ARCH-/P2-/SEARCH-/AI-CS- 等编号），docs/TODO.md 是 backlog 总入口
- 新子模块依赖写法：`groupId com.sakana + ${module.version}`；版本统一在父 pom dependencyManagement
- FeignClient 必须给唯一 contextId（曾多次 Bean 冲突）

## 已知坑 / 文档失真
- docs/TECHNICAL_SUMMARY.md 端口表是旧值（gateway 10008 / admin 10005 / message 10002 / stats 10006），与 application.yml 不一致，勿信
- 根目录存在大量 del-product-start*.log、hs_err_pid*.log、replay_pid*.log 等调试残留，非源码
- nacos-config/ 当前只剩 nacos_config_export_20260905201732 一份导出
