package com.sakana.cs.service.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakana.cs.feign.OrderFeignClient;
import com.sakana.web.vo.OrderPageResp;
import com.sakana.web.vo.R;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderHistoryTool {
    private final OrderFeignClient orderFeign;
    private final ObjectMapper objectMapper;

    /**
     * ★ BUG-020：查询当前用户的订单历史
     * userId 从消息【当前用户ID: xxx】中提取，通过 Tool 参数传入
     * 不再依赖 ThreadLocal（异步线程会丢失）
     */
    @Tool("查询当前登录用户的订单历史列表。返回订单列表，每条包含订单号、状态、时间、商品信息等。")
    public String getUserOrderHistory(
            @P("用户ID，从消息【当前用户ID: xxx】中提取") String userId,
            @P("最大返回数量，默认5") Integer maxResults) {
        Long uid = parseUserId(userId);
        if (uid == null) {
            log.warn("[OrderHistoryTool] userId 无效: {}", userId);
            return "[]";
        }
        int size = (maxResults != null && maxResults > 0) ? maxResults : 5;
        log.info("[OrderHistoryTool] userId={}, maxResults={}", uid, size);
        try {
            R<OrderPageResp> resp = orderFeign.getUserOrders(uid, 1, size);
            if (resp != null && resp.getData() != null && resp.getData().getRecords() != null) {
                return objectMapper.writeValueAsString(resp.getData().getRecords());
            }
        } catch (JsonProcessingException e) {
            log.error("[OrderHistoryTool] JSON序列化失败: {}", e.getMessage());
        } catch (Exception e) {
            log.error("[OrderHistoryTool] error={}", e.getMessage(), e);
        }
        return "[]";
    }

    private Long parseUserId(String uid) {
        if (uid == null || uid.isBlank() || "anonymous".equals(uid)) return null;
        try { return Long.parseLong(uid); } catch (Exception e) { return null; }
    }
}
