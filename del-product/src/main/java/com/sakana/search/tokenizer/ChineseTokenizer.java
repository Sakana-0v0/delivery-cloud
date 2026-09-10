package com.sakana.search.tokenizer;

import lombok.extern.slf4j.Slf4j;
import org.ansj.splitWord.analysis.ToAnalysis;
import org.ansj.domain.Term;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 中文分词器（基于 Ansj Seg）
 *
 * <p>取代 ES 端 IK 插件，分词在 Java 进程内完成。
 * <p>输入"番茄鸡蛋汤" → 输出"番茄 鸡蛋 汤"
 *
 * <p>使用 Ansj Seg（Maven: org.ansj:ansj_seg:5.1.6）
 * <p>官网：https://github.com/NLPchina/ansj_seg
 */
@Slf4j
@Component
public class ChineseTokenizer {

    /**
     * 对文本进行中文分词，返回空格分隔的词条
     *
     * @param text 原始文本（如商品名称）
     * @return 分词后的词条，逗号分隔
     */
    public String tokenize(String text) {
        if (text == null || text.trim().isEmpty()) return "";

        try {
            List<String> terms = ToAnalysis.parse(text).getTerms().stream()
                    .filter(t -> t.getName().length() > 1)           // 过滤单字
                    .filter(t -> !"w".equals(t.getNatureStr()))    // 过滤标点
                    .map(Term::getName)
                    .toList();

            return String.join(" ", terms);
        } catch (Exception e) {
            log.warn("[分词] 分词失败, text={}, err={}", text, e.getMessage());
            return text; // 降级：返回原文
        }
    }

    /**
     * 分词返回词条数组（用于搜索时构造 wildcard 查询词）
     */
    public String[] tokenizeArray(String text) {
        if (text == null || text.trim().isEmpty()) return new String[0];

        try {
            return ToAnalysis.parse(text).getTerms().stream()
                    .filter(t -> t.getName().length() > 1)
                    .filter(t -> !"w".equals(t.getNatureStr()))
                    .map(Term::getName)
                    .toArray(String[]::new);
        } catch (Exception e) {
            log.warn("[分词] 分词失败, text={}, err={}", text, e.getMessage());
            return new String[]{text}; // 降级：返回原文作为单一词条
        }
    }
}
