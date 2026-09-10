# 📋 工单 #AUTH-QQ-001：QQ 第三方登录集成（心月互联方案）

> **创建时间**：2026-09-05
> **完成时间**：2026-09-06（端到端联调通过）
> **影响服务**：del-user + 前端 Vue 项目 + del-gateway + del-common
> **预计工时**：2-3 小时（实际 ~3 小时含联调调试）
> **依赖**：无
> **状态**：✅ **已完成**
> **⚠️ 风险提示**：使用第三方代登录，30 天 token 需手动更换

---

## 一、⚠️ 方案风险提示

### 风险

| 风险 | 说明 | 影响 |
|------|------|------|
| 第三方依赖 | 心月互联（qq.wch666.com）挂了则登录全挂 | 高 |
| Token 30 天过期 | 文章说"默认 30 天"，需去心月互联手动续期 | 中 |
| 合规风险 | 绕过 QQ 官方审核机制 | 中 |
| 回调地址暴露 | URL 含 code + msg，可能被构造 | 低 |

### 缓解措施

- Token 放 Nacos 配置中心，更新时改一处即可
- 加告警（30 天到期前提醒续期）
- 加状态监控（每日跑 dry-run 调用）

---

## 二、API 契约（已验证 ✅）

### 心月互联 auth 接口

```
GET https://qq.wch666.com/api/qq.php?token={TOKEN}&msg={MSG}&display={pc|mobile}

Response: 302 重定向到 QQ 官方 OAuth
          https://graph.qq.com/oauth2.0/authorize?...
```

### 心月互联 get_user_info 接口（**实测通过**）

```
GET https://qq.wch666.com/api/get_user_info.php?code={CODE}

Response (成功):
{
  "ret": 0,
  "msg": "",
  "nickname": "鱼仔",
  "gender": "男",
  "gender_type": 2,
  "province": "广东",
  "city": "深圳",
  "year": "1990",
  "figureurl": "...",
  "figureurl_2": "...",  ← 100x100 头像
  "open_id": "F8F145364C962C7FAF6A798A92190D52"
}

Response (失败):
"error"  ← 纯文本
```

**判断逻辑**：`ret == 0` 视为成功

---

## 三、数据库变更

```sql
USE del_user_db;

-- 1. 加 QQ 相关字段
ALTER TABLE t_user
    ADD COLUMN open_id VARCHAR(64) DEFAULT NULL COMMENT 'QQ open_id，唯一',
    ADD COLUMN qq_nickname VARCHAR(100) DEFAULT NULL COMMENT 'QQ 昵称缓存',
    ADD COLUMN qq_avatar VARCHAR(512) DEFAULT NULL COMMENT 'QQ 头像 URL（100x100）',
    ADD UNIQUE KEY uk_open_id (open_id);

-- 2. QQ 用户无密码，password 改可空
ALTER TABLE t_user 
    MODIFY COLUMN password VARCHAR(100) DEFAULT NULL;

-- 3. 验证
DESCRIBE t_user;
```

---

## 四、配置文件变更

`del-user` 服务 Nacos 配置 `del-user.yml`：

```yaml
# ======== QQ 第三方登录配置（心月互联）========
qq:
  enabled: true
  token: dd1541f921c14cfc48827bc620e6256c  # 心月互联后台申请，30 天有效
  base-url: https://qq.wch666.com
  # 完整回调 URL（前端 + 后端 + 心月互联三方都要配置）
  callback-url: http://localhost:10010/api/v1/auth/qq/callback
  frontend-base-url: http://localhost:5173   # 用于 302 重定向
```

---

## 五、后端实现

### 5.1 `QqAuthController.java`

**路径**：`del-user/src/main/java/com/sakana/web/controllers/QqAuthController.java`

**核心逻辑**：
1. 心月互联 302 重定向到此接口（带 code）
2. 后端用 code 调心月互联 get_user_info 拿用户信息
3. 查找或创建本地用户
4. 生成 JWT
5. 302 重定向到前端成功页（带 token）

**关键点**：用 `@Qualifier("externalRestTemplate")` 注入专用 RestTemplate，避免 @LoadBalanced 把外部 URL 当服务名。

### 5.2 `UserService` 新增方法

```java
@Transactional
public User loginOrRegisterByQq(String openId, String nickname, String avatar) {
    // 1. 查 openId
    User existing = userRepository.findByOpenId(openId);
    if (existing != null) {
        // 老用户：更新昵称和头像
        return existing;
    }

    // 2. 新用户：创建账号
    User newUser = new User();
    newUser.setUsername("qq_" + openId.substring(0, Math.min(12, openId.length())).toLowerCase());
    newUser.setOpenId(openId);
    newUser.setQqNickname(nickname);
    newUser.setQqAvatar(avatar);
    newUser.setNickname(nickname);
    newUser.setStatus(1);
    // password 留空
    userRepository.insert(newUser);
    return newUser;
}
```

### 5.3 `User` 实体加字段

```java
@TableField("open_id") private String openId;
@TableField("qq_nickname") private String qqNickname;
@TableField("qq_avatar") private String qqAvatar;
```

---

## 六、前端实现

### 6.1 `LoginView.vue` 加 QQ 登录按钮

```vue
<!-- 第三方登录 -->
<div class="qq-login-section">
  <el-divider content-position="center">其他登录方式</el-divider>
  <el-button class="qq-login-btn" @click="qqLogin">
    <span class="qq-icon">🐧</span>
    QQ 登录
  </el-button>
</div>

<script setup>
function qqLogin() {
  const qqToken = import.meta.env.VITE_QQ_TOKEN
  const msg = Date.now().toString() + '_' + Math.random().toString(36).slice(2, 8)
  sessionStorage.setItem('qq_msg', msg)
  const url = `https://qq.wch666.com/api/qq.php?token=${qqToken}&msg=${msg}&display=pc`
  window.location.href = url
}
</script>
```

### 6.2 `QQSuccessView.vue`（新建）

**路径**：`src/views/QQSuccessView.vue`

```vue
<el-spin v-if="status === 'loading'" tip="QQ 登录中..." />
<el-result v-else-if="status === 'success'" status="success"
          title="QQ 登录成功"
          :sub-title="`欢迎回来，${nickname}！正在跳转首页...`">
  <template #extra>
    <el-button type="primary" @click="goHome">立即跳转</el-button>
  </template>
</el-result>
<el-result v-else status="error" title="登录失败" :sub-title="errorMsg">
  <template #extra>
    <el-button type="primary" @click="goLogin">返回登录页</el-button>
  </template>
</el-result>

<script setup>
onMounted(() => {
  if (route.query.error) {
    status.value = 'fail'
    errorMsg.value = String(route.query.error)
    return
  }
  const token = route.query.token
  if (!token) { /* fail */ return }
  localStorage.setItem(TOKEN_KEYS.C_ACCESS, String(token))
  localStorage.setItem('userInfo', JSON.stringify({ nickname: nick }))
  setTimeout(() => router.push('/'), 1500)
  status.value = 'success'
})
</script>
```

### 6.3 路由 + 环境变量

```typescript
// router/index.ts
{ path: '/login/qq-success', component: () => import('@/views/QQSuccessView.vue') }

// .env
VITE_QQ_TOKEN=dd1541f921c14cfc48827bc620e6256c
```

---

## 七、心月互联后台配置

| 配置项 | 值 |
|--------|-----|
| Token | `dd1541f921c14cfc48827bc620e6256c` |
| 回调地址 | `http://localhost:10010/api/v1/auth/qq/callback` |

---

## 八、🐛 联调期间修复的 Bug

### BUG-003：RestTemplate Bean 跨模块缺失

**症状**：
```
Parameter 2 of constructor in QqAuthController required a bean of type
'org.springframework.web.client.RestTemplate' that could not be found
```

**根因**：`RestTemplateConfig` 在 del-product，del-user 通过 pom 依赖隐式依赖。

**修复**：下沉 `RestTemplateConfig` 到 del-common，所有服务自动获得。

### BUG-004：网关拦截 QQ 回调

**症状**：
```json
{"code":401,"message":"缺少访问令牌","source":"del-gateway"}
```

**根因**：`/api/v1/auth/qq/**` 不在 `PathRoleRule.isPublic()` 白名单。

**修复**：`PathRoleRule` 加公开路径：
```java
if (path.startsWith("/api/v1/auth/qq/")) {
    return true;
}
```

### BUG-005：RestTemplate 把外部 URL 当服务名

**症状**：
```
Service Instance cannot be null, serviceId: qq.wch666.com
```

**根因**：`@LoadBalanced` 注解让 RestTemplate 把 URL 主机名当服务名查 Nacos，但 `qq.wch666.com` 不是微服务。

**修复**：`RestTemplateConfig` 新增 `externalRestTemplate` Bean：
```java
@Bean("externalRestTemplate")
public RestTemplate externalRestTemplate() {
    return new RestTemplate();  // 不带 @LoadBalanced
}
```
`QqAuthController` 用 `@Qualifier("externalRestTemplate")` 注入。

### BUG-006：前端 UI 组件名错误

**症状**：
```
Failed to resolve component: a-spin
```

**根因**：项目用 `element-plus`，工程师写了 `<a-spin>`（ant-design-vue 语法）。

**修复**：
- `<a-spin>` → `<el-spin>`
- `<a-result>` → `<el-result>`
- `<a-button>` → `<el-button>`

---

## 九、✅ 最终状态总结

| 阶段 | 状态 |
|------|------|
| 心月互联 API 响应格式确认 | ✅ JSON（实测） |
| 后端 QqAuthController 实现 | ✅ |
| 前端 LoginView + QQSuccessView 实现 | ✅ |
| 网关 PathRoleRule 白名单 | ✅ /api/v1/auth/qq/** |
| RestTemplate 拆分 | ✅ internal + external |
| 前端 UI 组件修复 | ✅ a- → el- |
| 端到端联调 | ✅ token 写入 localStorage |

---

## 十、签收

| 角色 | 操作 | 时间 |
|------|------|------|
| 工单创建 | Codex | 2026-09-05 |
| 前端实施 | 前端工程师 | 2026-09-05 |
| 后端联调 | 后端工程师 | 2026-09-05 |
| 联调通过 | 用户确认 | 2026-09-06 |

---

## 十一、待办（建议后续处理）

| 优先级 | 事项 | 工作量 |
|--------|------|--------|
| P1 | Token 30 天到期前告警机制（邮件/钉钉） | 1h |
| P1 | 心月互联服务可用性监控（每日 dry-run 调用） | 30min |
| P2 | 老用户绑定 QQ 功能（在个人中心） | 半天 |
| P3 | 官方 QQ 互联资质申请（长期方案） | 1-2 周 |