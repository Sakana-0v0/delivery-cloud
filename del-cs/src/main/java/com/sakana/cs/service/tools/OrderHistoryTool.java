package com.sakana.cs.service.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakana.cs.context.ChatContextHolder;
import com.sakana.cs.feign.OrderFeignClient;
import com.sakana.web.vo.OrderPageResp;
import com.sakana.web.vo.R;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * ★ P0-UserAuth重构：userId 由 ChatContextHolder 透传，LLM 不再需要关心身份。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderHistoryTool {
    private final OrderFeignClient orderFeign;
    private final ObjectMapper objectMapper;

    @Tool("查询当前登录用户的订单历史列表。无需传入用户ID，" +
          "工具会自动用当前登录身份查询属于该用户的订单。")
    public String getUserOrderHistory(@P("最大返回数量，默认5") Integer maxResults) {
        Long uid = ChatContextHolder.getUserIdAsLong();
        if (uid == null) {
            log.warn("[OrderHistoryTool] ChatContextHolder 未建立 userId，拒绝查询");
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
}