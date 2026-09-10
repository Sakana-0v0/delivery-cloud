package com.sakana.cs.prompt;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RefreshScope
@RequiredArgsConstructor
public class PromptManager {

    @Value("${cs.system-prompt:}")
    private String systemPromptFromConfig;

    @Value("${cs.fallback-response:}")
    private String fallbackResponse;

    private volatile String cachedPrompt;

    @PostConstruct
    public void init() {
        this.cachedPrompt = systemPromptFromConfig;
        log.info("[PromptManager] init, len={}", cachedPrompt.length());
    }

    public String getSystemPrompt() {
        if (cachedPrompt == null || cachedPrompt.isEmpty()) {
            cachedPrompt = systemPromptFromConfig;
        }
        return cachedPrompt;
    }

    public String getFallbackResponse() {
        return fallbackResponse;
    }
}
