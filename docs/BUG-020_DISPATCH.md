# 📧 #BUG-020 派工消息（直接复制发给你的后端工程师）

【派工】#BUG-020 订单查询真修复（5 个文件，预计 45 分钟）

═══════════════════════════════════════════
📋 工单：docs/WORK_ORDER_BUG-020.md
🎯 现象：订单查询返回"无法获取订单信息"
🎯 根因：AuthContext ThreadLocal 在异步线程丢失（#BUG-019 验收失误，实际未修复）
🎯 修复：userId 作为 @Tool 参数（不再依赖 ThreadLocal）
═══════════════════════════════════════════

【⚠️ 重要说明】
之前 #BUG-019 我说"验收通过"，**实际是假阳性**。工具内部 AuthContext.getUserId() 在异步线程返回 "anonymous"，第一行就 return List.of()，Feign 根本没调用。这次是真修复。

【⚠️ 执行顺序】
  1. 先检查 del-order OrderController 是否需要改（userId 从 JWT 改为 @RequestParam）
  2. 再改 del-cs 的 5 个文件
  3. 重启 del-cs
  4. curl 验证

═══════════════════════════════════════════
【第 1 步：先检查 del-order】

打开 E:\Idea_project\delivery-cloud\del-order\src\main\java\com\sakana\web\controllers\OrderController.java

确认：
- getMyOrders(page, size) 是从 JWT 取 userId 还是 @RequestParam？
- getByOrderNo(userId, orderNo) 是否支持？
- #BUG-019 加的 /internal/orders 端点是否还在？

如果是 JWT 取 userId，需要改成 @RequestParam Long userId。

如果 #BUG-019 的 internal 端点还在，需要删除。

把检查结果告诉我。

═══════════════════════════════════════════
【第 2 步：改 del-cs 的 5 个文件】

1️⃣ E:\Idea_project\delivery-cloud\del-cs\src\main\java\com\sakana\cs\web\ChatController.java

在 chat() 方法里，streamChat 调用前加：
```java
String userId = com.sakana.cs.context.AuthContext.getUserId();
String enhancedMessage = String.format("【当前用户ID: %s】%s", userId, message);
chatService.streamChat(userId, enhancedMessage, ...);
```

（userId 已经有了，message 改为 enhancedMessage）

2️⃣ E:\Idea_project\delivery-cloud\del-cs\src\main\java\com\sakana\cs\service\tools\OrderHistoryTool.java

整个文件替换为（完整代码见工单改动 2）：
- userId 从 @Tool 参数传入（不再用 AuthContext.getUserId()）
- 加 parseUserId() 工具方法

3️⃣ E:\Idea_project\delivery-cloud\del-cs\src\main\java\com\sakana\cs\service\tools\OrderDetailTool.java

整个文件替换为（完整代码见工单改动 3）：
- userId 从 @Tool 参数传入
- 加 parseUserId() 工具方法

4️⃣ E:\Idea_project\delivery-cloud\del-cs\src\main\java\com\sakana\cs\feign\OrderFeignClient.java

回滚 #BUG-019 的 internal 端点，加 userId 参数：
- 删 getInternalUserOrders
- 删 getInternalOrderByOrderNo
- getUserOrders 加 Long userId 参数（返回类型改 OrderPageResp）
- getOrderDetail 改名为 getByOrderNo，加 Long userId 参数

完整代码见工单改动 4。

5️⃣ Nacos del-cs-prompt.yml

推送新 Prompt（完整 YAML 见工单改动 5）。
关键内容：在 Prompt 开头加"用户消息包含【当前用户ID: xxx】标记"。
由架构师推送，不要你改。

═══════════════════════════════════════════
【第 3 步：重启 del-cs，curl 验证】

$body = '{"message":"我最近一笔订单吃的什么"}'
curl.exe -s -X POST "http://localhost:10011/api/v1/cs/chat" ^
  -H "Authorization: Bearer <sakana_jwt_token>" ^
  -H "Content-Type: application/json" ^
  -d $body --max-time 30

预期：tool 事件 + token 事件 + 最终包含真实订单数据

═══════════════════════════════════════════
📋 完成反馈：
  - "del-order 检查完成"（第 1 步）
  - "del-cs 代码完成"（第 2 步）
  - "del-cs 重启完成，curl 验证通过"（第 3 步）
═══════════════════════════════════════════
🎯 工单完成定义：
  - 问"我最近一笔订单吃的什么"返回真实订单（不再是空）
  - 后端日志显示 userId=2087088327483965445（不是 anonymous）
  - 不再有 [OrderHistoryTool] userId 无效 警告
═══════════════════════════════════════════
