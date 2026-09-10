# 📋 工单 #API-AUDIT-001：前后端字段对齐修复

> **创建时间**：2026-09-04
> **更新时间**：2026-09-04（补充前端 TS 类型联动说明）
> **报告依据**：`E:\Idea_project\delivery-cloud\docs\API_FIELD_AUDIT_REPORT_FINAL.md`
> **接收方**：后端工程师（P0-5）+ 前端工程师（P0-1~P0-4, P0-5前端侧, P1-2）

---

## 一、问题汇总

| 优先级 | 编号 | 问题 | 负责方 | 工作量 | 状态 |
|--------|------|------|--------|--------|------|
| P0 | P0-1 | B 端订单 `statusText` → `statusDesc` | 前端 | 5 min | 待修复 |
| P0 | P0-2 | B 端订单 `addressDetail` → `receiverAddress` | 前端 | 5 min | 待修复 |
| P0 | P0-3 | B 端订单 `finishTime` → `completeTime` | 前端 | 5 min | 待修复 |
| P0 | P0-4 | B 端订单项 `cover` → `productCover` | 前端 | 5 min | 待修复 |
| **P0** | **P0-5** | **AdminUserPageResp.records 泛型丢失 + 密码泄露风险** | **后端 + 前端** | **后端15min + 前端5min** | **双方均需修改** |
| P1 | P1-1 | 雪花 ID 精度（已被前端 string 吸收）| 观察 | - | 无需处理 |
| **P1** | **P1-2** | **reviewType 来源确认** | **前端** | **5 min** | **待修复** |
| **P1-3** | ~~admin 登录 userInfo 响应确认~~ | - | - | **✅ 已确认，无需操作** |

---

## 二、后端工单（P0-5）

### 任务单：AdminUserPageResp 泛型修复 + 密码泄露风险消除

**优先级**：P0
**预计工时**：15 分钟
**报告来源**：`E:\Idea_project\delivery-cloud\docs\API_FIELD_AUDIT_REPORT_FINAL.md`

---

### 1. 问题描述

文件：`E:\Idea_project\delivery-cloud\del-common\src\main\java\com\sakana\web\vo\AdminUserPageResp.java`

当前实现：
```java
@Data
public class AdminUserPageResp implements Serializable {
    private Long total;
    private Integer page;
    private Integer size;
    private List<?> records;   // ← 通配符泛型，前端无法推导类型
}
```

**双重影响**：
1. 前端 TypeScript 编译时类型丢失，`records.map()` 等操作报类型错误
2. 后端当前直接返回 `List<User>` 实体类，**包含 `password` 字段**——即使 setPassword(null) 后仍有序列化泄露风险

---

### 2. 修复方案

#### 步骤 1：新建 `AdminUserVO`（在 del-common）

文件路径：`E:\Idea_project\delivery-cloud\del-common\src\main\java\com\sakana\web\vo\AdminUserVO.java`

```java
package com.sakana.web.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 管理后台 - 用户列表 VO（仅含前端需要字段，避免 password 泄露）
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdminUserVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String username;
    private String nickname;
    private String phone;
    private String email;
    private String avatar;
    private Integer status;
    private LocalDateTime lastLoginTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
```

#### 步骤 2：修改 `AdminUserPageResp` 泛型

文件路径：`E:\Idea_project\delivery-cloud\del-common\src\main\java\com\sakana\web\vo\AdminUserPageResp.java`

```java
package com.sakana.web.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
public class AdminUserPageResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long total;
    private Integer page;
    private Integer size;
    private List<AdminUserVO> records;  // ← 改为具体类型，前端可推导
}
```

#### 步骤 3：修改 `UserServiceImpl.adminGetPage()` 转换逻辑

文件路径：`E:\Idea_project\delivery-cloud\del-user\src\main\java\com\sakana\services\impl\UserServiceImpl.java`

**原代码**（约第 200-220 行）：
```java
// 清空 password 字段（但仍可能通过 Jackson 序列化泄露）
pageResult.getRecords().forEach(u -> u.setPassword(null));

AdminUserPageResp resp = new AdminUserPageResp();
resp.setTotal(pageResult.getTotal());
resp.setPage(page);
resp.setSize(size);
resp.setRecords(pageResult.getRecords());  // ← 直接传 List<User>，含 password
return resp;
```

**修改后**：
```java
import com.sakana.web.vo.AdminUserVO;
// ... 其他 imports

@Override
public AdminUserPageResp adminGetPage(AdminUserListQuery query) {
    int page = query.getPage() == null ? 1 : query.getPage();
    int size = query.getSize() == null ? 10 : query.getSize();

    Page<User> pageParam = new Page<>(page, size);
    pageParam.addOrder(OrderItem.desc("create_time"));

    LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(User::getIsDeleted, 0);
    if (query.getStatus() != null) {
        wrapper.eq(User::getStatus, query.getStatus());
    }
    if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
        String kw = query.getKeyword();
        wrapper.and(w -> w.like(User::getUsername, kw)
                .or().like(User::getNickname, kw)
                .or().like(User::getPhone, kw)
                .or().like(User::getEmail, kw));
    }

    Page<User> pageResult = userMapper.selectPage(pageParam, wrapper);

    // ★ 转换为 AdminUserVO（避免 password 字段泄露）
    List<AdminUserVO> voList = pageResult.getRecords().stream()
            .map(this::toAdminUserVO)
            .collect(Collectors.toList());

    AdminUserPageResp resp = new AdminUserPageResp();
    resp.setTotal(pageResult.getTotal());
    resp.setPage(page);
    resp.setSize(size);
    resp.setRecords(voList);
    return resp;
}

private AdminUserVO toAdminUserVO(User user) {
    AdminUserVO vo = new AdminUserVO();
    vo.setId(user.getId());
    vo.setUsername(user.getUsername());
    vo.setNickname(user.getNickname());
    vo.setPhone(user.getPhone());
    vo.setEmail(user.getEmail());
    vo.setAvatar(user.getAvatar());
    vo.setStatus(user.getStatus());
    vo.setLastLoginTime(user.getLastLoginTime());
    vo.setCreateTime(user.getCreateTime());
    vo.setUpdateTime(user.getUpdateTime());
    return vo;
}
```

---

### 3. 编译验证

```powershell
cd E:\Idea_project\delivery-cloud

# 1. del-common 必须先 install，让其他模块能引用到新 jar
mvn clean install -pl del-common -am -DskipTests -s settings.xml

# 2. del-user 和 del-admin 用 compile 即可（已依赖 del-common）
mvn clean compile -pl del-user -am -DskipTests -s settings.xml
mvn clean compile -pl del-admin -am -DskipTests -s settings.xml
```

> **说明**：`install` 会将 jar 放入本地 Maven 仓库（`~/.m2/repository`），其他模块通过 `-am`（also-make）依赖它时才能找到新的 `AdminUserVO` 类。`compile` 仅编译当前模块，不发布 jar。

预期：全部 `BUILD SUCCESS`。

---

### 4. 重启顺序

```
① del-common（install 后自动完成，实际上不需要单独启动）
② del-user（先启动，让它加载新的 AdminUserVO 类）
③ del-admin（后启动，确保 del-user 已就绪）
```

---

### 5. 验证清单

- [ ] del-user 启动日志无 `ClassCastException` 或 `ClassNotFoundException`
- [ ] del-admin 启动日志无报错
- [ ] 调用 `GET /api/v1/admin/users` 返回的 `records` **不包含 `password` 字段**
- [ ] 前端用户列表正常显示用户名/昵称/手机/邮箱/状态/注册时间
- [ ] 前端 TypeScript 编译无类型错误（需同步更新 TS 类型，详见第三节）

---

### 6. 风险评估

| 风险 | 等级 | 缓解措施 |
|------|------|----------|
| 类型转换遗漏字段 | 低 | 完整复制所有 AdminUserVO 中定义的字段 |
| Jackson 序列化差异 | 低 | 字段名一致即可 |
| del-common jar 版本冲突 | 中 | 严格按顺序执行 install → compile → 重启 |
| 前端未同步更新 TS 类型 | **高** | 详见第三节，**前端必须同步修改** |

---

## 三、前端工单

### ⚠️ 重要：P0-5 前端侧必须同步修改

后端 P0-5 修改后，API 返回的 `records` 类型从 `List<?>`（类型丢失）变为 `List<AdminUserVO>`（明确类型）。**前端若不同步更新 TypeScript 类型，将出现编译错误或运行时类型推断异常。**

---

### P0-5 前端：更新用户管理 TS 类型定义

文件：`E:\VSCode_workspace\Delivery\src\api\admin\user.ts`

**修改内容**：将 `AdminUser` 接口（或类似命名）替换为与后端 `AdminUserVO` 对应的 TypeScript 类型

```typescript
// src/api/admin/user.ts

// ★ 新增：与后端 AdminUserVO 对齐的类型定义（password 字段已排除）
export interface AdminUserVO {
  id: number
  username: string
  nickname: string
  phone: string
  email: string
  avatar?: string
  status: number
  lastLoginTime: string
  createTime: string
  updateTime: string
}

// ★ 修改：分页响应类型，使用 AdminUserVO 作为 records 的泛型
export interface AdminUserPageResp {
  total: number
  page: number
  size: number
  records: AdminUserVO[]  // ← 原来是 unknown[] 或 any[]
}
```

---

### P0-1 ~ P0-4：B 端订单字段名修正

| 原字段（错） | 新字段（对） | 所在文件 |
|-------------|-------------|---------|
| `statusText` | `statusDesc` | `order.ts` + `OrderManageView.vue` |
| `addressDetail` | `receiverAddress` | `order.ts` + `OrderManageView.vue` |
| `finishTime` | `completeTime` | `order.ts` + `OrderManageView.vue` |
| `item.cover` | `item.productCover` | `OrderManageView.vue` |

文件：
- `E:\VSCode_workspace\Delivery\src\api\admin\order.ts`
- `E:\VSCode_workspace\Delivery\src\views\admin\OrderManageView.vue`

---

### P1-2：reviewType 处理方案

**问题**：`reviewType` 来源不明确，可能出现在订单项或其他位置但无实际用途。

**处理方案（二选一）**：

**方案 A（推荐）**：如果 `reviewType` 在前端有实际使用场景（评价类型筛选等），从 `/api/v1/reviews/switch` 接口获取并使用。

**方案 B**：如果 `reviewType` 在前端**没有任何地方使用**，直接从接口返回数据中移除相关字段引用，前端代码中删除该字段的绑定。

> **建议**：先在前端全局搜索 `reviewType`，确认是否有实际使用，再决定方案。

---

## 四、P1-3 已确认（无需操作）

### 确认结论

后端 `AdminLoginResp` 已正确包含 `userInfo` 字段，无需任何修改。

```java
@Data
public class AdminLoginResp {
    private String accessToken;
    private String refreshToken;
    private Long expiresIn;
    private UserInfo userInfo;  // ✅ 已有

    @Data
    public static class UserInfo {
        private Long id;
        private String username;
        private String nickname;
        private String role;
    }
}
```

**验证命令**：
```bash
curl -X POST http://localhost:10010/api/v1/admin/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"superadmin\",\"password\":\"123456\"}"
```

---

## 五、文件清单

### 后端文件

| 文件 | 操作 |
|------|------|
| `del-common/.../web/vo/AdminUserVO.java` | **新建** |
| `del-common/.../web/vo/AdminUserPageResp.java` | 修改泛型 `List<?>` → `List<AdminUserVO>` |
| `del-user/.../services/impl/UserServiceImpl.java` | 新增 `toAdminUserVO()` 转换方法 |

### 前端文件

| 文件 | 操作 | 对应后端 |
|------|------|---------|
| `src/api/admin/user.ts` | 新增 `AdminUserVO` 接口 + 修改分页类型 | P0-5 后端 |
| `src/api/admin/order.ts` | 字段名修正：`statusText` → `statusDesc` 等 | P0-1~P0-4 |
| `src/views/admin/OrderManageView.vue` | 字段名修正 + `item.cover` → `item.productCover` | P0-1~P0-4 |
| `src/views/admin/UserManageView.vue`（如有） | reviewType 使用确认或移除 | P1-2 |

---

## 六、联调验证清单（后端 P0-5 完成后执行）

| # | 验证项 | 命令/方法 | 预期结果 |
|---|--------|---------|---------|
| 1 | del-user 启动正常 | IDEA 控制台 | 无 ClassNotFoundException |
| 2 | del-admin 启动正常 | IDEA 控制台 | 无异常 |
| 3 | admin 登录 | `POST /api/v1/admin/auth/login` | 返回 userInfo |
| 4 | 用户列表无 password | `GET /api/v1/admin/users` | records 中无 password 字段 |
| 5 | 前端 TS 编译 | `npm run build` | 无 TS 错误 |

---

**工单创建人**：后端工程师（响应审计报告）
**最后更新**：2026-09-04（补充前端 TS 类型联动说明）
**接收方**：后端工程师（P0-5）+ 前端工程师（P0-1~P0-5, P1-2）
