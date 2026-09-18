package com.sakana.cs.context;

import lombok.extern.slf4j.Slf4j;

/**
 * ChatContext 的 ThreadLocal 持有器。
 *
 * <p>关键点：
 * <ul>
 *   <li>由 ChatController 在 SSE 入口设置；SSE 完成/超时/异常时清理</li>
 *   <li>工具层（OrderDetailTool/OrderHistoryTool）通过本 holder 读取用户身份，
 *       <strong>不再依赖 LLM 从用户消息文本里提取 userId</strong></li>
 *   <li>LangChain4j 流式调用链（TokenStream.start）使用与调用方相同线程执行 Tool，
 *       所以 ThreadLocal 在 Tool 调用栈内可读</li>
 *   <li>AuthFeignRequestInterceptor 也会回退读取本 holder 注入 Authorization</li>
 * </ul>
 *
 * <p>注意：若未来使用 Project Reactor 全异步化，本类应替换为 Reactor Context。
 */
@Slf4j
public final class ChatContextHolder {

    private static final ThreadLocal<ChatContext> CTX = new ThreadLocal<>();

    private ChatContextHolder() {}

    public static void set(ChatContext ctx) {
        CTX.set(ctx);
        if (ctx != null) {
            log.debug("[ChatContextHolder] set userId={}", ctx.getUserId());
        } else {
            log.debug("[ChatContextHolder] set null (clear semantics)");
            CTX.remove();
        }
    }

    public static ChatContext get() {
        return CTX.get();
    }

    public static String getUserId() {
        ChatContext ctx = CTX.get();
        return ctx == null ? null : ctx.getUserId();
    }

    public static Long getUserIdAsLong() {
        ChatContext ctx = CTX.get();
        return ctx == null ? null : ctx.userIdAsLong();
    }

    public static String getToken() {
        ChatContext ctx = CTX.get();
        return ctx == null ? null : ctx.getToken();
    }

    public static void clear() {
        ChatContext prev = CTX.get();
        CTX.remove();
        if (prev != null) {
            log.debug("[ChatContextHolder] clear userId={}", prev.getUserId());
        }
    }
}