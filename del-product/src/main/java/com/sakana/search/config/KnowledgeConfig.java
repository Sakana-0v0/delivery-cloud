package com.sakana.search.config;

import com.sakana.search.service.KnowledgeBaseService.ExpansionRules;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 知识库配置启用（#SEARCH-001）
 *
 * <p>显式启用 {@link ExpansionRules} 的 {@code @ConfigurationProperties} 绑定，
 * 让 {@link com.sakana.search.service.KnowledgeBaseService} 能够通过构造函数注入。
 *
 * <p>对应 Nacos 配置：
 * <pre>
 * search:
 *   expansion:
 *     rules:
 *       清淡:
 *         terms: [少油, 少盐, 清淡]
 *         minProtein: 5
 * </pre>
 *
 * @author sakana
 * @since 2026-09-05
 */
@Configuration
@EnableConfigurationProperties(ExpansionRules.class)
public class KnowledgeConfig {
}

