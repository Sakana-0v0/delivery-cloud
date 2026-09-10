package com.sakana.cs.service.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakana.cs.feign.OrderFeignClient;
import com.sakana.web.vo.OrderVO;
import com.sakana.web.vo.R;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderDetailTool {
    private final OrderFeignClient orderFeign;
    private final ObjectMapper objectMapper;

    /**
     * ★ BUG-020：查询指定订单详情
     * userId 从消息【当前用户ID: xxx】中提取，通过 Tool 参数传入
     * 不再依赖 ThreadLocal（异步线程会丢失）
     */
    @Tool("查询指定订单的详细信息。输入业务订单号（如 ORD20260904TEST_REVIEW_03），返回该订单的完整信息，包括商品列表、价格、状态、时间等。")
    public String getOrderDetail(
            @P("用户ID，从消息【当前用户ID: xxx】中提取") String userId,
            @P("业务订单号（如 ORD20260904TEST_REVIEW_03）") String orderNo) {
        Long uid = parseUserId(userId);
        if (uid == null) {
            log.warn("[OrderDetailTool] userId 无效: {}", userId);
            return "null";
        }
        log.info("[OrderDetailTool] userId={}, orderNo={}", uid, orderNo);
        try {
            R<OrderVO> resp = orderFeign.getByOrderNo(uid, orderNo);
            if (resp != null && resp.getData() != null) {
                return objectMapper.writeValueAsString(resp.getData());
            }
        } catch (JsonProcessingException e) {
            log.error("[OrderDetailTool] JSON序列化失败: {}", e.getMessage());
        } catch (Exception e) {
            log.error("[OrderDetailTool] error={}", e.getMessage(), e);
        }
        return "null";
    }

    private Long parseUserId(String uid) {
        if (uid == null || uid.isBlank() || "anonymous".equals(uid)) return null;
        try { return Long.parseLong(uid); } catch (Exception e) { return null; }
    }
}
