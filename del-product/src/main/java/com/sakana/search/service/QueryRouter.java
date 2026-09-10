package com.sakana.search.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Service;

/**
 * 查询意图（#SEARCH-001）
 */
@Service
public class QueryRouter {

    /**
     * 意图类型枚举
     */
    public enum Intent {
        /** 普通关键词搜索 */
        KEYWORD,
        /** 营养筛选 */
        NUTRITION,
        /** 分类筛选 */
        CATEGORY,
        /** 组合查询 */
        MIXED
    }

    @Data
    @AllArgsConstructor
    public static class QueryIntent {
        private Intent type;
        private String primaryQuery;
    }

    /**
     * 根据原始查询判断搜索意图
     */
    public QueryIntent route(String rawQuery) {
        if (rawQuery == null || rawQuery.trim().isEmpty()) {
            return new QueryIntent(Intent.KEYWORD, "");
        }

        String q = rawQuery.trim();

        // 营养类关键词
        if (q.contains("热量") || q.contains("蛋白") || q.contains("脂肪") ||
            q.contains("低卡") || q.contains("健身") || q.contains("减肥")) {
            return new QueryIntent(Intent.NUTRITION, q);
        }

        // 分类关键词
        if (q.contains("快餐") || q.contains("甜品") || q.contains("饮品") ||
            q.contains("水果") || q.contains("便当") || q.contains("沙拉")) {
            return new QueryIntent(Intent.CATEGORY, q);
        }

        return new QueryIntent(Intent.KEYWORD, q);
    }
}

