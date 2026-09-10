package com.sakana.search.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * DashScope API 配置（#SEARCH-001）
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "dashscope")
public class DashScopeConfig {

    private String apiKey;

    private String embeddingModel = "text-embedding-v3";

    private Integer embeddingDim = 1024;
}
