package com.sakana.search.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 知识库服务（#SEARCH-001）
 *
 * <p>#BUG-007 修复：ExpansionRules 改为可选注入（ES 模块暂未启用），
 * KnowledgeBaseService 不再强制依赖 ExpansionRules Bean。
 */
@Slf4j
@Service
public class KnowledgeBaseService {

    /**
     * 扩展规则配置（从 Nacos search-knowledge-base.yml 注入）
     * <p>前缀：search.expansion
     */
    @Data
    @ConfigurationProperties(prefix = "search.expansion")
    public static class ExpansionRules {
        private Map<String, RuleEntry> rules;
    }

    /**
     * 规则条目
     */
    @Data
    public static class RuleEntry {
        private List<String> terms;
        private Integer minProtein;
    }

    /**
     * #BUG-007 修复：不再 final，默认空实例
     */
    private ExpansionRules expansionRules = new ExpansionRules();

    /**
     * #BUG-007 修复：删除构造器注入，改为可选 setter
     */
    public KnowledgeBaseService() {
    }

    /**
     * 可选注入（如果 Nacos 中有 search.expansion.* 配置）
     */
    @Autowired(required = false)
    public void setExpansionRules(ExpansionRules expansionRules) {
        if (expansionRules != null) {
            this.expansionRules = expansionRules;
            log.info("[知识库] 已加载 ExpansionRules 配置");
        }
    }

    /**
     * 获取某个词的扩展词列表
     */
    public List<String> getExpandedTerms(String keyword) {
        if (expansionRules == null || expansionRules.getRules() == null) {
            return List.of();
        }
        RuleEntry entry = expansionRules.getRules().get(keyword);
        if (entry == null || entry.getTerms() == null) {
            return List.of();
        }
        return entry.getTerms();
    }

    /**
     * 判断关键词是否命中知识库规则
     */
    public boolean isRuleKeyword(String keyword) {
        if (expansionRules == null || expansionRules.getRules() == null) {
            return false;
        }
        return expansionRules.getRules().containsKey(keyword);
    }
}