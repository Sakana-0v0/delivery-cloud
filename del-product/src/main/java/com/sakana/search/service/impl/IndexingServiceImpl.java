package com.sakana.search.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sakana.dao.entity.Category;
import com.sakana.dao.entity.Product;
import com.sakana.dao.mapper.CategoryMapper;
import com.sakana.dao.mapper.ProductMapper;
import com.sakana.search.client.EmbeddingClient;
import com.sakana.search.document.DishDocument;
import com.sakana.search.dto.IndexTask;
import com.sakana.search.service.IndexingService;
import com.sakana.search.tokenizer.ChineseTokenizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 索引构建服务实现（#SEARCH-001）
 *
 * <p>核心职责：
 * <ul>
 *   <li>全量扫描 t_product 表</li>
 *   <li>Java 端 Ansj 分词生成 nameTokens / categoryTokens</li>
 *   <li>调用 DashScope Embedding 生成向量</li>
 *   <li>批量写入 ES dish 索引</li>
 * </ul>
 *
 * <p>ElasticsearchOperations 用 ObjectProvider 包装，避免 ES 不可用时启动失败
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {

    private final ProductMapper productMapper;
    private final CategoryMapper categoryMapper;
    private final EmbeddingClient embeddingClient;
    private final ChineseTokenizer chineseTokenizer;
    private final ObjectProvider<ElasticsearchOperations> elasticsearchOperationsProvider;

    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger failCount = new AtomicInteger(0);
    private volatile String currentStatus = "IDLE";
    private volatile int totalCount = 0;

    @Override
    @Async
    public CompletableFuture<IndexTask> rebuildAllIndexAsync() {
        IndexTask task = rebuildAll();
        return CompletableFuture.completedFuture(task);
    }

    @Override
    public IndexTask rebuildAll() {
        log.info("[索引重建] 开始全量重建...");
        successCount.set(0);
        failCount.set(0);
        currentStatus = "RUNNING";

        IndexTask task = new IndexTask();
        task.setStartTime(LocalDateTime.now());
        task.setStatus("RUNNING");

        try {
            // 1. 全量扫描上架商品（status=0 为上架，status=1 为下架）
            List<Product> products = productMapper.selectList(
                    new LambdaQueryWrapper<Product>()
                            .eq(Product::getStatus, 0)
            );
            task.setTotal(products.size());
            totalCount = products.size();
            log.info("[索引重建] 待索引商品数: {}", products.size());

            if (products.isEmpty()) {
                task.setSuccess(0);
                task.setFailed(0);
                task.setStatus("SUCCESS");
                task.setEndTime(LocalDateTime.now());
                currentStatus = "SUCCESS";
                return task;
            }

            // 预加载所有分类，构建 categoryId -> name 映射
            List<Category> categories = categoryMapper.selectList(null);
            Map<Long, String> categoryNameMap = categories.stream()
                    .collect(Collectors.toMap(Category::getId, Category::getName, (a, b) -> a));

            // 2. 分批处理（每批 50 条）
            int batchSize = 50;
            for (int i = 0; i < products.size(); i += batchSize) {
                List<Product> batch = products.subList(i, Math.min(i + batchSize, products.size()));
                processBatch(batch, categoryNameMap);
            }

            task.setSuccess(successCount.get());
            task.setFailed(failCount.get());
            task.setStatus("SUCCESS");
            task.setEndTime(LocalDateTime.now());
            currentStatus = "SUCCESS";
            log.info("[索引重建] 完成: total={}, success={}, failed={}",
                    products.size(), successCount.get(), failCount.get());

        } catch (Exception e) {
            log.error("[索引重建] 失败", e);
            task.setStatus("FAILED");
            task.setErrorMsg(e.getMessage());
            task.setEndTime(LocalDateTime.now());
            currentStatus = "FAILED";
        }

        return task;
    }

    private void processBatch(List<Product> products, Map<Long, String> categoryNameMap) {
        log.info("[索引重建] [诊断] processBatch 开始, batchSize={}", products.size());
        List<DishDocument> docs = new ArrayList<>();
        List<String> textsForEmbedding = new ArrayList<>();

        for (Product p : products) {
            DishDocument doc = new DishDocument();
            String fid = p.getFid();
            if (fid == null || fid.isBlank()) {
                log.warn("[索引重建] 商品 fid 为空，跳过: product={}", p.getName());
                continue;
            }
            doc.setId(fid);  // 直接用 fid 字符串（兼容数字和UUID）
            doc.setName(p.getName());
            doc.setDescription(p.getDescription());
            String categoryName = categoryNameMap.getOrDefault(p.getCategoryId(), "");
            doc.setCategory(categoryName);
            doc.setAvailable(p.getStatus() == 0);
            doc.setCalories(p.getCalories());
            doc.setProtein(p.getProtein());
            doc.setFat(p.getFat());

            doc.setUpdatedAt(LocalDateTime.now());

            // Java 端 Ansj 分词
            doc.setNameTokens(chineseTokenizer.tokenize(p.getName()));
            doc.setCategoryTokens(chineseTokenizer.tokenize(categoryName));
            // BUG-007：描述分词（用于属性词搜索，如"清淡"、"少油"
            String description = Optional.ofNullable(p.getDescription()).orElse("");
            doc.setPropertyTokens(chineseTokenizer.tokenize(description));

            docs.add(doc);
            textsForEmbedding.add(p.getName() + " " + categoryName + " " +
                    Optional.ofNullable(p.getDescription()).orElse(""));
        }

        if (docs.isEmpty()) return;

        // 批量生成 Embedding
        log.info("[索引重建] [诊断] 调用 embedBatch, textsCount={}", textsForEmbedding.size());
        try {
            List<List<Float>> embeddings = embeddingClient.embedBatch(textsForEmbedding);
            log.info("[索引重建] [诊断] embedBatch 返回, embeddingsSize={}", embeddings.size());
            for (int i = 0; i < docs.size(); i++) {
                if (i < embeddings.size()) {
                    docs.get(i).setEmbedding(embeddings.get(i));
                }
            }
        } catch (Exception e) {
            log.warn("[索引重建] Embedding 失败，跳过向量: error={}", e.getMessage());
        }

        // 改用 IndexOperations + save + 强制 refresh（修复 BUG-004：原 saveAll 异常被吞）
        ElasticsearchOperations esOps = elasticsearchOperationsProvider.getIfAvailable();
        if (esOps == null) {
            log.warn("[索引重建] ES 客户端不可用，跳过本批 {} 条", docs.size());
            failCount.addAndGet(docs.size());
            return;
        }
        try {
            IndexOperations indexOps = esOps.indexOps(DishDocument.class);
            if (!indexOps.exists()) {
                indexOps.create();
                indexOps.putMapping(indexOps.createMapping(DishDocument.class));
                log.info("[索引重建] 创建 dish 索引");
            }
            esOps.save(docs, IndexCoordinates.of("dish"));
            esOps.indexOps(DishDocument.class).refresh();
            successCount.addAndGet(docs.size());
            log.info("[索引重建] 写入 ES 成功: {} 条", docs.size());
        } catch (Exception e) {
            log.error("[索引重建] ES 写入失败: docCount={}, error={}", docs.size(), e.getMessage(), e);
            failCount.addAndGet(docs.size());
        }
    }

    /**
     * 获取最后一次任务状态
     */
    public IndexTask getLastTask() {
        IndexTask task = new IndexTask();
        task.setStatus(currentStatus);
        task.setSuccess(successCount.get());
        task.setFailed(failCount.get());
        task.setTotal(totalCount);
        return task;
    }
}



