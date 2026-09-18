package com.sakana.cs.context;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

/**
 * Chat 上下文（按用户粒度），解决 SSE 异步 Tool 调用时 AuthContext ThreadLocal 丢失问题。
 *
 * <p>由 ChatController 在 SSE 入口处 set，SSE 完成/超时/异常时由 ChatController 清理。
 * <p>工具层（OrderDetailTool/OrderHistoryTool 等）通过 ChatContextHolder 读取用户身份，
 *    <strong>不再依赖 LLM 从用户消息文本里提取 userId</strong>。
 *
 * <p>如果未来切换到 Project Reactor 全异步，可无缝替换为 Reactor Context。
 */
@Getter
@ToString
@AllArgsConstructor
public class ChatContext {

    /** 用户 ID（来自 JWT subject） */
    private final String userId;

    /** 原始 JWT Token，用于下游 Feign 透传到 del-order/del-product 做 X-User-* 鉴权 */
    private final String token;

    /** 会话开始时间（用于排查超时/泄漏） */
    private final long startMillis;

    public boolean hasToken() {
        return token != null && !token.isBlank();
    }

    /** 解析 userId 为 Long；失败（含 anonymous）返回 null */
    public Long userIdAsLong() {
        if (userId == null || userId.isBlank() || "anonymous".equals(userId)) {
            return null;
        }
        try {
            return Long.parseLong(userId);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
