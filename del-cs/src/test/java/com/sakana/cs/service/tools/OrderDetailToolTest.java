package com.sakana.cs.service.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakana.cs.context.ChatContext;
import com.sakana.cs.context.ChatContextHolder;
import com.sakana.cs.feign.OrderFeignClient;
import com.sakana.web.vo.OrderVO;
import com.sakana.web.vo.R;
import dev.langchain4j.agent.tool.Tool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * OrderDetailTool 测试 - 验证 UserAuth 重构后的关键不变量：
 * <ul>
 *   <li>工具方法签名不再包含 userId 参数（防 prompt 注入）</li>
 *   <li>工具内部从 ChatContextHolder 读取 userId</li>
 *   <li>ChatContextHolder 未建立时返回安全错误</li>
 * </ul>
 */
@DisplayName("OrderDetailTool 用户身份透传测试")
@ExtendWith(MockitoExtension.class)
class OrderDetailToolTest {

    @Mock
    private OrderFeignClient orderFeign;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private OrderDetailTool tool;

    @BeforeEach
    void setUp() {
        tool = new OrderDetailTool(orderFeign, objectMapper);
    }

    @AfterEach
    void tearDown() {
        ChatContextHolder.clear();
    }

    @Test
    @DisplayName("工具方法签名不包含 userId 参数（防 prompt 注入）")
    void testMethodSignature_noUserIdParam() throws NoSuchMethodException {
        Method method = OrderDetailTool.class.getMethod("getOrderDetail", String.class);
        Class<?>[] params = method.getParameterTypes();

        assertEquals(1, params.length, "Tool 方法必须只有 orderNo 一个参数");
        assertEquals(String.class, params[0]);
    }

    @Test
    @DisplayName("@Tool 注解描述中不暴露 userId 字段")
    void testToolAnnotation_noUserIdHint() throws NoSuchMethodException {
        Method method = OrderDetailTool.class.getMethod("getOrderDetail", String.class);
        Tool toolAnnotation = method.getAnnotation(Tool.class);

        assertNotNull(toolAnnotation);
        String description = String.join(" ", toolAnnotation.value());
        assertFalse(description.toLowerCase().contains("userid"),
                "@Tool 描述不能引导 LLM 寻找 userId 字段");
    }

    @Test
    @DisplayName("ChatContextHolder 未建立时返回安全错误")
    void testNoContext_returnsSafeError() {
        ChatContextHolder.clear();
        String result = tool.getOrderDetail("ORD-123");
        assertNotNull(result);
        assertTrue(result.contains("error"), "无上下文应返回 error 提示");
        verifyNoInteractions(orderFeign);
    }

    @Test
    @DisplayName("正常路径：从 ChatContextHolder 取 userId 调下游 FeignClient")
    void testNormalPath_routesByChatContext() throws Exception {
        ChatContextHolder.set(new ChatContext("42", "mocked-jwt", System.currentTimeMillis()));

        OrderVO orderVO = new OrderVO();
        orderVO.setOrderNo("ORD-100");
        R<OrderVO> response = R.ok(orderVO);
        when(orderFeign.getByOrderNo(eq(42L), eq("ORD-100"))).thenReturn(response);

        String result = tool.getOrderDetail("ORD-100");

        assertNotNull(result);
        assertTrue(result.contains("ORD-100"));
        verify(orderFeign).getByOrderNo(42L, "ORD-100");
    }

    @Test
    @DisplayName("prompt 注入：orderNo 被恶意改写不影响 userId 路由")
    void testPromptInjection_orderNoCantOverrideUserId() throws Exception {
        ChatContextHolder.set(new ChatContext("42", "mocked-jwt", System.currentTimeMillis()));

        when(orderFeign.getByOrderNo(eq(42L), anyString())).thenReturn(R.ok(new OrderVO()));

        tool.getOrderDetail("ORD-OTHER-USER-9999");

        verify(orderFeign).getByOrderNo(42L, "ORD-OTHER-USER-9999");
    }

    @Test
    @DisplayName("Feign 调用异常时不抛异常，返回 null（避免 SSE 流中断）")
    void testFeignException_swallowed() {
        ChatContextHolder.set(new ChatContext("42", "tok", System.currentTimeMillis()));
        when(orderFeign.getByOrderNo(anyLong(), anyString())).thenThrow(new RuntimeException("downstream down"));

        String result = tool.getOrderDetail("ORD-X");
        assertEquals("null", result);
    }
}
