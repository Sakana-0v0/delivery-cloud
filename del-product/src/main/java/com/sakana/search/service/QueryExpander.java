package com.sakana.search.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 查询词扩展器（#SEARCH-001）
 *
 * <p>基于知识库规则扩展搜索词，提升召回率
 * <p>例如：用户搜"清淡" → 扩展为"少油 少盐 清淡 素"
 */
@Service
@RequiredArgsConstructor
public class QueryExpander {

    private final KnowledgeBaseService knowledgeBaseService;
    private final QueryParser queryParser;

    /**
     * 对原始查询进行知识库扩展，返回扩展后的词条列表
     */
    public List<String> expand(String rawQuery) {
        List<String> expanded = new ArrayList<>();

        // 先解析出纯关键词
        ParsedQuery parsed = queryParser.parse(rawQuery);
        String keyword = parsed.getKeyword();

        if (keyword == null || keyword.isEmpty()) {
            return expanded;
        }

        // 精确匹配知识库
        if (knowledgeBaseService.isRuleKeyword(keyword)) {
            expanded.addAll(knowledgeBaseService.getExpandedTerms(keyword));
        } else {
            // 没有命中知识库，返回原始词
            expanded.add(keyword);
        }

        return expanded;
    }

    /**
     * 扩展并去重
     */
    public String expandToString(String rawQuery) {
        List<String> list = expand(rawQuery);
        return String.join(" ", list);
    }
}
