package com.sakana.search.client;

import java.util.List;

/**
 * Embedding 客户端接口（#SEARCH-001）
 *
 * <p>抽象层，Phase 2 可替换为 Python 实现或其他向量服务
 */
public interface EmbeddingClient {

    /**
     * 单条文本生成向量
     */
    List<Float> embed(String text);

    /**
     * 批量生成向量（每批最多 25 条）
     */
    List<List<Float>> embedBatch(List<String> texts);
}
