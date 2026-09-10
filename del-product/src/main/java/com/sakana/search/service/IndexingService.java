package com.sakana.search.service;

import com.sakana.search.dto.IndexTask;

/**
 * 索引构建服务接口（#SEARCH-001）
 */
public interface IndexingService {

    /**
     * 执行全量重建索引（同步）
     */
    IndexTask rebuildAll();

    /**
     * 触发全量重建索引（异步）
     */
    java.util.concurrent.CompletableFuture<IndexTask> rebuildAllIndexAsync();

    /**
     * 获取最后一次重建任务的状态（#BUG-009 修复：移到接口，避免 AOP 代理强转 ClassCastException）
     */
    IndexTask getLastTask();
}