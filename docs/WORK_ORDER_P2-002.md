# 📋 工单 #P2-002：清理 VoteConsumer 死代码

> **创建时间**：2026-09-04
> **完成时间**：2026-09-04
> **优先级**：P2（代码清理，非阻塞）
> **接收方**：后端工程师
> **影响服务**：del-product
> **预计工时**：30 分钟
> **依赖工单**：无
> **代码净变更**：-125 行
> **状态**：✅ **已完成**

---

## 一、问题描述

### 1.1 背景

经过 #PROD-VOTE-008 → #PROD-VOTE-010 的演进，vote 的计数逻辑已经完全改为**同步 Redis Lua**，MQ 路径被废弃：

| 旧路径 | 新路径 |
|--------|--------|
| vote() → MQ sendLikeEvent → VoteConsumer.handleLike → incrementCount | vote() → sync Lua |
| vote() → MQ sendBadEvent → VoteConsumer.handleBad → incrementCount | vote() → sync Lua |
| vote() → MQ sendCancelEvent → VoteConsumer.handleCancel → invalidateLocal | vote() → sync Lua |

### 1.2 当前状态

- `VoteConsumer.handleLike / handleBad / handleCancel` 三个分支原来是**死代码**
- `VoteConsumer` 现在只处理 `vote_persist` 事件，应该改名或拆分

---

## 二、修复方案

**采用方案 A**：删除 `VoteConsumer.java`，保留 `VotePersistConsumer.java`（#PROD-VOTE-010 已建）。

---

## 三、具体改动

### 3.1 文件改动清单

| 文件 | 操作 | 行数 |
|------|------|------|
| `del-product/src/main/java/com/sakana/review/mq/VoteConsumer.java` | **删除** | -80 |
| `del-product/src/main/java/com/sakana/review/mq/VoteProducer.java` | 删除 3 个死方法 | -30 |
| `del-product/src/main/java/com/sakana/review/mq/VoteEvent.java` | 删除 3 个死工厂方法 | -15 |

---

## 四、验收清单（全部通过 ✅）

- [x] `VoteConsumer.java` 死代码删除（或整个文件删除）
- [x] `VoteProducer.sendLikeEvent / sendBadEvent / sendCancelEvent` 删除
- [x] `del-product` 编译通过
- [x] 重启 `del-product`，启动无错误
- [x] 9 种状态转移场景回归测试通过
- [x] `t_review_log` 写入正确（异步落库链路仍工作）

---

## 五、签收

| 角色 | 操作 | 时间 |
|------|------|------|
| 工单创建 | Codex | 2026-09-04 |
| 后端执行 | 后端工程师 | 2026-09-04 |
| 验证完成 | 后端工程师 + 用户 | 2026-09-04 |

---

## 六、最终成果

- ✅ 清理 125 行死代码
- ✅ 业务代码 0 行业务改动
- ✅ 不影响任何功能（vote 9 种状态转移仍正常）
- ✅ 代码可读性提升（不再有迷惑性的 dead branch）

---

**工单归档**。