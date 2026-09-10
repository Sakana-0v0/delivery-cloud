package com.sakana.cs.service.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakana.cs.feign.ProductSearchFeignClient;
import com.sakana.search.dto.SearchResponse;
import com.sakana.web.vo.R;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SearchDishesTool {
    private final ProductSearchFeignClient searchFeign;
    private final ObjectMapper objectMapper;

    @Tool("根据关键词搜索菜品，返回匹配的商品列表，包含名称、价格、热量等信息。")
    public String searchDishes(@P("搜索关键词") String query) {
        log.info("[SearchDishesTool] query={}", query);
        try {
            R<SearchResponse> resp = searchFeign.search(query, 1, 5);
            log.info("[SearchDishesTool] resp={}, code={}, data={}",
                resp, resp == null ? "null" : resp.getCode(),
                resp == null ? "null" : resp.getData());
            if (resp != null && resp.getData() != null && resp.getData().getProducts() != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> products = (List<Map<String, Object>>) (List<?>) resp.getData().getProducts();
                log.info("[SearchDishesTool] products.size={}", products.size());
                return objectMapper.writeValueAsString(products);
            }
            log.warn("[SearchDishesTool] 搜索返回空: resp={}", resp);
        } catch (JsonProcessingException e) {
            log.error("[SearchDishesTool] JSON序列化失败: {}", e.getMessage());
        } catch (Exception e) {
            log.error("[SearchDishesTool] error={}", e.getMessage(), e);
        }
        return "[]";
    }
}

