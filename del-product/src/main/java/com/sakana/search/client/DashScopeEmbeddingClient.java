package com.sakana.search.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.sakana.search.config.DashScopeConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DashScope Embedding 客户端实现（#SEARCH-001）
 *
 * <p>使用 HTTP REST 直接调用阿里云 DashScope Text Embedding v3 API
 * <p>官方文档：https://help.aliyun.com/zh/model-studio/developer-reference/use-text-embedding-v3
 *
 * <p>API Endpoint: https://dashscope.aliyuncs.com/api/v1/services/embeddings/text-embedding/text-embedding
 *
 * <p>#BUG-008 修复：DashScope API 限制每批最多 10 个文本（input.contents ≤ 10）。
 * 超过限制会返回 400 InvalidParameter。
 * embedBatch 现在分批发送，每批 10 个。
 */
@Slf4j
@Component
public class DashScopeEmbeddingClient implements EmbeddingClient {

    private static final String API_URL = "https://dashscope.aliyuncs.com/api/v1/services/embeddings/text-embedding/text-embedding";

    /** DashScope 单次请求最大文本数 */
    private static final int BATCH_SIZE = 10;

    private final DashScopeConfig dashScopeConfig;
    private final RestTemplate restTemplate;

    public DashScopeEmbeddingClient(
            DashScopeConfig dashScopeConfig,
            @Qualifier("externalRestTemplate") RestTemplate restTemplate) {
        this.dashScopeConfig = dashScopeConfig;
        this.restTemplate = restTemplate;
    }

    @Override
    public List<Float> embed(String text) {
        List<List<Float>> results = embedBatch(List.of(text));
        return results.isEmpty() ? List.of() : results.get(0);
    }

    @Override
    public List<List<Float>> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) return List.of();
        if (dashScopeConfig.getApiKey() == null || dashScopeConfig.getApiKey().isBlank()) {
            log.warn("[DashScope] API Key 未配置，返回零向量");
            return texts.stream().map(t -> Collections.nCopies(dashScopeConfig.getEmbeddingDim(), 0f)).toList();
        }

        try {
            List<List<Float>> allResults = new ArrayList<>();
            int totalBatches = (texts.size() + BATCH_SIZE - 1) / BATCH_SIZE;

            for (int i = 0; i < texts.size(); i += BATCH_SIZE) {
                int end = Math.min(i + BATCH_SIZE, texts.size());
                List<String> batch = texts.subList(i, end);

                log.info("[DashScope] 准备调用 Embedding API, batch=[{}/{}], textsCount={}",
                        i / BATCH_SIZE + 1, totalBatches, batch.size());

                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("model", dashScopeConfig.getEmbeddingModel());
                requestBody.put("input", Map.of("texts", batch));

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("Authorization", "Bearer " + dashScopeConfig.getApiKey());
                headers.set("DashScope-Async", "false");

                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

                ResponseEntity<String> response = restTemplate.exchange(
                        API_URL,
                        HttpMethod.POST,
                        entity,
                        String.class
                );

                List<List<Float>> batchResult = parseEmbeddingResponse(response.getBody());
                allResults.addAll(batchResult);
            }

            log.info("[DashScope] Embedding 调用成功, vectorsCount={}, dim={}",
                    allResults.size(), allResults.isEmpty() ? 0 : allResults.get(0).size());
            return allResults;

        } catch (HttpClientErrorException e) {
            log.error("[DashScope] HTTP 错误: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("[DashScope] HTTP 错误: " + e.getStatusCode());
        } catch (Exception e) {
            log.error("[DashScope] Embedding 批量失败: error={}", e.getMessage());
            throw new RuntimeException("[DashScope] Embedding 批量失败: " + e.getMessage());
        }
    }

    /**
     * 解析 DashScope API 响应
     */
    private List<List<Float>> parseEmbeddingResponse(String json) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            JsonNode root = objectMapper.readTree(json);
            JsonNode embeddings = root.path("output").path("embeddings");
            List<List<Float>> results = new ArrayList<>();
            for (JsonNode item : embeddings) {
                JsonNode vectorNode = item.path("embedding");
                List<Float> vector = new ArrayList<>();
                for (JsonNode f : vectorNode) {
                    vector.add((float) f.asDouble());
                }
                results.add(vector);
            }
            return results;
        } catch (JsonProcessingException e) {
            log.error("[DashScope] 响应解析失败: json={}, error={}", json, e.getMessage());
            throw new RuntimeException("[DashScope] Embedding 响应解析失败");
        }
    }
}
