# 📋 工单 #P2-001：微服务依赖治理 — 排查所有 pom.xml 错误依赖

> **创建时间**：2026-09-04
> **优先级**：P2（架构治理，非阻塞）
> **接收方**：后端工程师
> **影响服务**：全部 9 个业务服务
> **预计工时**：2 小时
> **依赖工单**：无
> **代码净变更**：N 个 pom.xml 删依赖 + 可能涉及的代码调整

---

## 一、问题描述

### 1.1 背景

#ARCH-FEIGN-001 修复了 `del-order → del-product` 的错误依赖。但这只排查了一个服务对，可能还有其他业务服务存在同样的反模式：

```
del-product 被多个业务服务依赖 ❌
    ↓
那些服务启动时会扫描到 del-product 的所有 Bean
    ↓
潜在的"用了错误数据源调对了方法"或"Bean 重复注册"问题
```

### 1.2 治理目标

建立**微服务依赖白名单**：
- ✅ **允许**：业务服务 → `del-common`（共享接口/VO）
- ❌ **禁止**：业务服务 → 业务服务（只能通过 Feign 调用）

---

## 二、排查范围

| 服务 | 当前 pom 依赖 | 是否合规 | 待处理 |
|------|--------------|---------|--------|
| del-user | del-common, del-payment? | 待查 | ? |
| del-order | del-common | ✅ 已修 | 无 |
| del-product | del-common | 待查 | ? |
| del-message | del-common | 待查 | ? |
| del-payment | del-common | 待查 | ? |
| del-stats | del-common, del-order?, del-product? | 待查 | ? |
| del-file | del-common | 待查 | ? |
| del-admin | del-common | 待查 | ? |
| del-gateway | del-common | 待查 | ? |

---

## 三、执行步骤

### 步骤 1：扫描所有 pom.xml

```bash
# 在 E:\Idea_project\delivery-cloud\ 目录下执行
Get-ChildItem -Recurse -Filter "pom.xml" | 
    Where-Object { $_.FullName -notmatch "target" } |
    ForEach-Object {
        $content = Get-Content $_.FullName -Raw
        $deps = Select-String -InputObject $content -Pattern "artifactId>(del-\w+)</artifactId"
        Write-Host "=== $($_.FullName) ==="
        $deps | ForEach-Object { Write-Host "  - $($_.Matches.Groups[1].Value)" }
    }
```

预期输出类似：

```
=== del-order\pom.xml ===
  - del-common           ✅ 合规
=== del-product\pom.xml ===
  - del-common           ✅ 合规
  - del-payment?         ❌ 待确认
=== del-stats\pom.xml ===
  - del-common           ✅ 合规
  - del-order            ❌ 不合规
  - del-product          ❌ 不合规
...
```

### 步骤 2：识别错误依赖

对每个非 `del-common` 的业务服务依赖，记录：

| 服务 | 错误依赖 | 实际用途（grep） |
|------|---------|----------------|
| del-X | del-Y | grep "import com.sakana.Y" |

### 步骤 3：制定修复方案

每个错误依赖，参考 #ARCH-FEIGN-001 的方法：
1. 把共享接口/VO 移到 `del-common`
2. 业务调用改用 Feign Client
3. 移除 pom.xml 中的错误依赖

---

## 四、产出物

| 产出 | 内容 |
|------|------|
| 1. 依赖审计报告 | 每个服务的依赖矩阵 |
| 2. 修复工单 | 每个错误依赖生成单独工单（可批量） |
| 3. 架构红线文档 | 写入 `docs/architecture-rules.md`，禁止业务服务直接依赖 |

---

## 五、验证清单

- [ ] 所有 pom.xml 扫描完成
- [ ] 每个服务的依赖矩阵记录在工单附录
- [ ] 每个错误依赖都有对应的修复工单
- [ ] `docs/architecture-rules.md` 编写完成
- [ ] CI 增加检查：禁止业务服务之间的直接依赖（可选）

---

## 六、签收

| 角色 | 操作 | 时间 |
|------|------|------|
| 工单创建 | Codex | 2026-09-04 |
| 后端执行 | _待填_ | |
| 治理完成 | _待填_ | |