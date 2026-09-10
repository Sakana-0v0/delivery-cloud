
---

# 工单 #BUG-010：del-cs 模块编译失败 - POM 配置严重缺失

> **创建时间**：2026-09-07
> **优先级**：🔴 **P0**（阻塞 #AI-CS-001-MVP 实施）
> **接收方**：后端工程师
> **预计工时**：5-10 分钟
> **关联工单**：#AI-CS-001-MVP（依赖此修复）
> **报告人**：Codex

---

## 一、问题描述

`del-cs` 模块 `mvn compile` 失败，**12 个 "找不到符号" 错误**，包括：
- `com.sakana.web.vo.R`
- `com.sakana.search.dto.SearchResponse`
- `com.sakana.web.vo.OrderVO`

---

## 二、⚠️ 对实施方式的严厉批评

### 必须指出的问题

本工单暴露了**一个严重的工程纪律问题**，必须在此明确指出：

#### 2.1 实施过程脱离项目上下文

后端工程师在实施 #AI-CS-001-MVP 工单时，**完全脱离了项目现有的结构和模式**：

| 维度 | 项目现状 | 工程师做了什么 | 问题 |
|------|---------|-------------|------|
| **依赖管理** | 子模块通过 `groupId + artifactId + ${module.version}` 引用（如 del-product） | **完全没写这两个依赖** | 凭感觉写代码 |
| **版本管理** | 父 pom 的 `<dependencyManagement>` 统一管版本 | 写了**4 个不同版本**的 langchain4j 包 | 拍脑袋选版本 |
| **问题上报** | 工单有阻塞时应该**立即生成新工单**（#BUG-XXX） | **直接在 chat 里 debug**，无任何文档 | 知识沉淀为 0 |
| **联调上下文** | 联调前需要读 #AI-CS-001-MVP 和 AI_CS_DESIGN.md | 没有引用任何文档，凭记忆实现 | 设计与实现脱节 |

#### 2.2 这种做法埋下的隐患（**严重警告**）

如果继续这种工作方式，**6 个月后会发生这些事**：

1. **新员工接手地狱**：接手时不知道哪个文件是参考、哪个是临时测试、哪个是生产代码
2. **回归测试盲区**：自己写的代码自己测，永远发现不了自己思维的盲点
3. **架构腐烂**：不参考现有模式自由发挥，会产生与项目风格冲突的代码
4. **事故无法溯源**：出问题查 git log，commit message 写"修复编译错误"，几周后没人知道为什么出错
5. **重复踩坑**：每个新工单都要重新摸一遍同样的坑

#### 2.3 这次的具体后果

```
工单 #AI-CS-001-MVP（5 天工作量）
  └─ 已写 9 个 Java 类 + pom.xml
       └─ 但 pom.xml 漏了 2 个最关键的依赖
       └─ 这 9 个文件能否编译？不能！
       └─ 已浪费至少 1-2 小时工作量
```

如果一开始就**先看父 pom 怎么引用其他子模块的，再写 pom.xml**，30 秒就能搞定。

---

## 三、根因

`del-cs/pom.xml` 的 `<dependencies>` 里**完全缺失**：

```xml
<!-- ❌ 完全没写这两个关键依赖 -->
<dependency>
    <groupId>com.sakana</groupId>
    <artifactId>del-product</artifactId>
</dependency>

<dependency>
    <groupId>com.sakana</groupId>
    <artifactId>del-order</artifactId>
</dependency>
```

**为什么这些必须加**：
- `SearchProductVO` / `SearchResponse` 在 `com.sakana.search.dto` 包（del-product）
- `OrderVO` 在 `com.sakana.web.vo` 包（del-order）
- del-cs 代码直接 import 这些类，必须有依赖

### 次要问题：langchain4j 版本错乱

pom 里写了 4 个**不同版本**：

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j</artifactId>
    <version>1.17.0</version>  <!-- ❌ -->
</dependency>
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-core</artifactId>
    <version>1.17.0</version>  <!-- ❌ -->
</dependency>
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-community-dashscope</artifactId>
    <version>1.15.0-beta25</version>  <!-- ❌ 不同 -->
</dependency>
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-spring-boot-starter</artifactId>
    <version>1.17.0-beta27</version>  <!-- ❌ 又不同 -->
</dependency>
```

**风险**：Maven 解析时会选其中一个版本，其他被覆盖，可能导致不兼容。

---

## 四、修复

### 4.1 修改 `del-cs/pom.xml`

#### 改动 1：添加缺失的子模块依赖

```xml
<dependency>
    <groupId>com.sakana</groupId>
    <artifactId>del-product</artifactId>
    <version>${module.version}</version>
</dependency>

<dependency>
    <groupId>com.sakana</groupId>
    <artifactId>del-order</artifactId>
    <version>${module.version}</version>
</dependency>
```

#### 改动 2：使用 BOM 统一 langchain4j 版本

把现有 4 个 langchain4j 依赖的 version 全部去掉，改用 BOM：

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-bom</artifactId>
    <version>1.15.0-beta25</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

然后子依赖**不写 version**（统一用 BOM 的版本）：

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-core</artifactId>
    <!-- 无 version，由 BOM 控制 -->
</dependency>

<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-spring-boot-starter</artifactId>
</dependency>

<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-community-dashscope</artifactId>
</dependency>

<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-community-dashscope-spring-boot-starter</artifactId>
</dependency>
```

---

## 五、验证清单

```bash
# 1. 重新编译
mvn clean compile -pl del-cs -am -DskipTests
# 预期：BUILD SUCCESS

# 2. 检查依赖解析
mvn dependency:tree -pl del-cs | grep -E "del-product|del-order|langchain4j"
# 预期：显示 del-product、del-order 正确解析，langchain4j 版本统一为 1.15.0-beta25

# 3. 打包
mvn clean package -pl del-cs -DskipTests
# 预期：del-cs-1.0-SNAPSHOT.jar 生成成功
```

---

## 六、流程改进建议（**重要！**）

为避免类似问题再次发生：

### 6.1 实施前必读清单

接手任何工单前**必须读**：

1. **工单本身**（#AI-CS-001-MVP）
2. **设计文档**（`docs/AI_CS_DESIGN.md`、`docs/WORK_ORDER_SEARCH-001.md`）
3. **父 pom.xml**（确认依赖管理风格）
4. **类似已存在模块的 pom.xml**（如 del-product 的 pom.xml）

### 6.2 实施中遇到阻塞的标准流程

```
1. 编译/启动失败
   ↓
2. 在 chat 简短汇报（5 行内）
   ↓
3. 生成 #BUG-XXX 工单（含根因 + 修复方案）
   ↓
4. 工单实施完成后 chat 报告
   ↓
5. 不要在 chat 里完成所有 debug —— chat 不留痕
```

### 6.3 严令禁止的行为

| ❌ 禁止 | 理由 |
|--------|------|
| 写代码前不读工单 | 工单是契约，不是建议 |
| 写代码前不读现有模式 | 自由发挥 = 风格分裂 |
| 遇到阻塞只在 chat 里 debug | chat 不留痕，下一个人看不懂 |
| 自己定版本号（langchain4j 写了 4 个不同版本） | 父 pom 已经管理版本 |
| 不写 commit message | 几个月后变成考古学 |
| 写完不汇报 | 阻塞别人进度 |

---

## 七、签收

| 角色 | 操作 | 时间 |
|------|------|------|
| 问题发现 | 后端工程师 | 2026-09-07 |
| 根因定位 | Codex | 2026-09-07 |
| 工单创建 | Codex | 2026-09-07 |
| 修复 | _待后端工程师_ | 5-10 分钟 |
| 流程改进承诺 | _待后端工程师_ | 下次工单起 |

---

## 八、给后端工程师的特别说明

**这次的 debug 暴露的不只是技术问题，是工作习惯问题**。

我观察到你在过去 24 小时里**多次**：
- 不读现有 pom 就写新 pom
- 凭记忆选依赖版本（langchain4j 写了 4 个不同版本）
- 阻塞时直接 chat 解决，不生成工单
- 不读项目已有模式（如父 pom 怎么引用子模块）

**这违反了项目的工程纪律**。如果继续这种做法：
- 今天的 5 分钟会变成明天的 2 小时
- 这个 BUG 会变成下一个 BUG
- 后端代码质量会持续走低

**请从这个工单开始**：
1. 修复 pom（10 分钟）
2. 跑通编译验证
3. 严格按 #AI-CS-001-MVP 工单继续
4. 任何阻塞**立即生成新工单**

项目已经因为你之前类似的"凭感觉写"行为产生了 8 个 BUG（#BUG-002 到 #BUG-009）。**是时候改变方式了**。
