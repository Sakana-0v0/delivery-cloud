package com.sakana.cs.service.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakana.cs.context.ChatContextHolder;
import com.sakana.cs.feign.OrderFeignClient;
import com.sakana.web.vo.OrderVO;
import com.sakana.web.vo.R;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * ★ P0-UserAuth重构：userId 不再由 LLM 从消息文本提取并回填，
 * 而是由 ChatContextHolder 透传，避免提示词注入导致越权查询。
 *
 * <p>工具方法签名不再暴露 userId 参数，LLM 无需关心身份路由。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderDetailTool {
    private final OrderFeignClient orderFeign;
    private final ObjectMapper objectMapper;

    @Tool("查询指定订单的详细信息。输入业务订单号（如 ORD20260904TEST_REVIEW_03）。" +
          "工具会自动使用当前登录用户的身份验证订单所有权；不要尝试传入或猜测用户ID。")
    public String getOrderDetail(@P("业务订单号，如 ORD20260904TEST_REVIEW_03") String orderNo) {
        Long uid = ChatContextHolder.getUserIdAsLong();
        if (uid == null) {
            log.warn("[OrderDetailTool] ChatContextHolder 未建立 userId，拒绝查询");
            return "{\"error\":\"未识别用户身份\"}";
        }
        if (orderNo == null || orderNo.isBlank()) {
            log.warn("[OrderDetailTool] orderNo 为空: userId={}", uid);
            return "{\"error\":\"订单号不能为空\"}";
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
}