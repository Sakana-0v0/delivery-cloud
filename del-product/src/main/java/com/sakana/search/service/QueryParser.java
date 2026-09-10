package com.sakana.search.service;

import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 查询条件解析器（#SEARCH-001）
 *
 * <p>从原始查询字符串中提取结构化条件：
 * <ul>
 *   <li>热量筛选：小于 XXX 卡</li>
 *   <li>蛋白筛选：高于 XXg 蛋白</li>
 *   <li>脂肪筛选：少于 XXg 脂肪</li>
 * </ul>
 */
@Service
public class QueryParser {

    // 匹配"少于200卡"、"低于300卡路里"等
    private static final Pattern CALORIES_PATTERN =
            Pattern.compile("(?:少于|低于|小于|不超过|不高于)(\\d+)(?:卡|卡路里)?");
    // 匹配"高于20g蛋白"、"不少于15g蛋白质"
    private static final Pattern PROTEIN_PATTERN =
            Pattern.compile("(?:高于|不少于|不低于|多于)(\\d+(?:\\.\\d+)?)(?:g)?蛋白");
    // 匹配"少于10g脂肪"
    private static final Pattern FAT_PATTERN =
            Pattern.compile("(?:少于|低于|少于)(\\d+(?:\\.\\d+)?)(?:g)?脂肪");

    public ParsedQuery parse(String rawQuery) {
        ParsedQuery result = new ParsedQuery();
        if (rawQuery == null || rawQuery.trim().isEmpty()) {
            return result;
        }

        String query = rawQuery.trim();

        // 提取热量
        Matcher calMatcher = CALORIES_PATTERN.matcher(query);
        if (calMatcher.find()) {
            result.setMaxCalories(Integer.parseInt(calMatcher.group(1)));
            query = query.substring(0, calMatcher.start()) + query.substring(calMatcher.end());
        }

        // 提取蛋白
        Matcher proteinMatcher = PROTEIN_PATTERN.matcher(query);
        if (proteinMatcher.find()) {
            result.setMinProtein(Float.parseFloat(proteinMatcher.group(1)));
            query = query.substring(0, proteinMatcher.start()) + query.substring(proteinMatcher.end());
        }

        // 提取脂肪
        Matcher fatMatcher = FAT_PATTERN.matcher(query);
        if (fatMatcher.find()) {
            result.setMaxFat(Float.parseFloat(fatMatcher.group(1)));
            query = query.substring(0, fatMatcher.start()) + query.substring(fatMatcher.end());
        }

        result.setKeyword(query.trim());
        return result;
    }
}
