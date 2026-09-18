package com.sakana.cs.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ChatContextHolder ThreadLocal 行为测试。
 *
 * <p>关键不变量：
 * <ul>
 *   <li>set 后同线程 get 必须能拿到</li>
 *   <li>clear 后必须返回 null</li>
 *   <li>ChatContext.userIdAsLong() 在 "anonymous"/"null"/空白时返回 null</li>
 *   <li>ChatContext.userIdAsLong() 在非法字符串时返回 null</li>
 * </ul>
 */
@DisplayName("ChatContextHolder ThreadLocal 单元测试")
class ChatContextHolderTest {

    @AfterEach
    void tearDown() {
        ChatContextHolder.clear();
    }

    @Test
    @DisplayName("set/get/clear 生命周期")
    void testSetGetClear() {
        assertNull(ChatContextHolder.getUserId(), "初始应为空");

        ChatContextHolder.set(new ChatContext("100", "tok-100", System.currentTimeMillis()));
        assertEquals("100", ChatContextHolder.getUserId());
        assertEquals("100", ChatContextHolder.get().getUserId());

        ChatContextHolder.clear();
        assertNull(ChatContextHolder.getUserId(), "clear 后必须为 null");
        assertNull(ChatContextHolder.get());
    }

    @Test
    @DisplayName("getUserIdAsLong 解析数字 userId")
    void testGetUserIdAsLong_validNumber() {
        ChatContextHolder.set(new ChatContext("12345", "tok", System.currentTimeMillis()));
        assertEquals(12345L, ChatContextHolder.getUserIdAsLong());
    }

    @Test
    @DisplayName("getUserIdAsLong 对 anonymous 返回 null")
    void testGetUserIdAsLong_anonymous() {
        ChatContextHolder.set(new ChatContext("anonymous", "tok", System.currentTimeMillis()));
        assertNull(ChatContextHolder.getUserIdAsLong());
    }

    @Test
    @DisplayName("getUserIdAsLong 对空白字符串返回 null")
    void testGetUserIdAsLong_blank() {
        ChatContextHolder.set(new ChatContext("   ", "tok", System.currentTimeMillis()));
        assertNull(ChatContextHolder.getUserIdAsLong());
    }

    @Test
    @DisplayName("getUserIdAsLong 对非数字字符串返回 null（不抛异常）")
    void testGetUserIdAsLong_nonNumeric() {
        ChatContextHolder.set(new ChatContext("not-a-number", "tok", System.currentTimeMillis()));
        assertNull(ChatContextHolder.getUserIdAsLong(), "非数字应安全返回 null，不抛 NumberFormatException");
    }

    @Test
    @DisplayName("ChatContext.hasToken 仅在 token 非空时为 true")
    void testHasToken() {
        ChatContext withToken = new ChatContext("1", "tok-1", System.currentTimeMillis());
        ChatContext emptyToken = new ChatContext("1", "", System.currentTimeMillis());
        ChatContext nullToken = new ChatContext("1", null, System.currentTimeMillis());

        assertTrue(withToken.hasToken());
        assertFalse(emptyToken.hasToken());
        assertFalse(nullToken.hasToken());
    }

    @Test
    @DisplayName("set(null) 等价于 clear")
    void testSetNull() {
        ChatContextHolder.set(new ChatContext("1", "tok", System.currentTimeMillis()));
        assertEquals("1", ChatContextHolder.getUserId());

        ChatContextHolder.set(null);
        assertNull(ChatContextHolder.getUserId(), "set(null) 必须清空 ThreadLocal");
    }

    @Test
    @DisplayName("getToken 在无上下文时返回 null")
    void testGetToken_nullSafe() {
        assertNull(ChatContextHolder.getToken());
        ChatContextHolder.set(new ChatContext("1", "my-token", System.currentTimeMillis()));
        assertEquals("my-token", ChatContextHolder.getToken());
        ChatContextHolder.clear();
        assertNull(ChatContextHolder.getToken());
    }
}
