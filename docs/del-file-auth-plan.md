# del-file 服务鉴权增强方案

**文档状态**：📌 已封存（待后续实现）
**创建日期**：2026-09-02
**项目**：delivery-cloud 外卖微服务平台
**优先级**：P2
**预估工时**：待定

---

## 一、背景说明

当前 del-file 文件上传服务定位为**公共服务**，不做角色隔离，主要服务于：
- 管理员上传商品图片（商品上下架）
- 未来爬虫批量上传菜品图片

当前设计已验证可行，重启后 permitAll() 生效即可正常工作。

---

## 二、封存原因

1. **当前阶段优先级**：核心业务流程联调尚未完成，文件上传功能本身能 work 即可
2. **鉴权设计复杂性**：若要加鉴权，需要考虑：
   - 上传接口需要管理员 Token（网关层已有粗粒度控制）
   - 查询接口需要前端能公开访问（商品图片展示）
   - 删除接口需要管理员权限
   - 各操作的角色隔离设计
3. **架构调整成本**：若加鉴权，需要同步调整网关路由规则和 del-file 安全配置

---

## 三、鉴权方案设计（预留）

### 3.1 方案一：网关层统一鉴权（推荐）

**思路**：在网关层根据路径区分角色，del-file 保持无感知

| 接口 | 网关路径规则 | 角色要求 |
|------|------------|---------|
| 上传 | /api/v1/files/upload | ADMIN |
| 查询 | /api/v1/files/info | 公开 |
| 删除 | /api/v1/files/{md5} | ADMIN |

**实现方式**：
1. PathRoleRule.isPublic() 中添加 /api/v1/files/info 公开
2. PathRoleRule.checkRole() 中对 /api/v1/files/upload、/api/v1/files/{md5} 要求 ADMIN 角色
3. del-file 端保持 permitAll() 或调整为只允许内部调用

### 3.2 方案二：del-file 独立鉴权

**思路**：在 del-file 端自己做 Spring Security 权限控制

`java
// FileSecurityConfig.java
.authorizeHttpRequests(auth -> auth
    .requestMatchers(HttpMethod.POST, "/api/v1/files/upload").hasRole("ADMIN")
    .requestMatchers(HttpMethod.DELETE, "/api/v1/files/**").hasRole("ADMIN")
    .requestMatchers(HttpMethod.GET, "/api/v1/files/info").permitAll()
    .anyRequest().authenticated()
)
`

**实现方式**：
1. 排除 SecurityAutoConfiguration 改为精细化配置
2. 注入 JwtVerifier 解析网关透传的 X-User-Role Header
3. 根据角色控制接口访问

---

## 四、待解决问题

| 问题 | 说明 | 影响 |
|------|------|------|
| 前端图片展示如何拿到 URL | 前端无 Token，无法调用需认证的查询接口 | 需设计公开查询接口 |
| 爬虫上传如何带 Token | 爬虫脚本需要携带管理员 Token | 需提供 Token 获取方式 |
| 删除接口的幂等性 | 防止误删和恶意删除 | 需二次确认或日志记录 |

---

## 五、实现前提

1. ✅ 核心业务流程联调完成
2. ✅ 文件上传功能测试通过
3. ✅ 菜品图片全部上传完毕
4. ✅ 爬虫功能实现（可能需要 Token）
5. ✅ 评审确认鉴权必要性

---

## 六、文档更新记录

| 日期 | 版本 | 更新内容 |
|------|------|---------|
| 2026-09-02 | v0.1 | 初始版本，创建文档并封存 |

---

*本文档已封存，待后续阶段根据实际需求决定是否启用鉴权方案。*