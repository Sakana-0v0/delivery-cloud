package com.sakana.search.service.impl;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.json.JsonData;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sakana.dao.entity.Category;
import com.sakana.dao.entity.Product;
import com.sakana.dao.mapper.CategoryMapper;
import com.sakana.dao.mapper.ProductMapper;
import com.sakana.search.document.DishDocument;
import com.sakana.search.dto.SearchProductVO;
import com.sakana.search.dto.SearchRequest;
import com.sakana.search.dto.SearchResponse;
import com.sakana.search.service.*;
import com.sakana.search.tokenizer.ChineseTokenizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 搜索服务实现（#SEARCH-001 / #SEARCH-001-VO）
 *
 * <p>ElasticsearchOperations 用 ObjectProvider 包装，避免 ES 不可用时启动失败
 * <p>使用 SearchProductVO 合并 MySQL + ES 数据给前端
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final ObjectProvider<ElasticsearchOperations> elasticsearchOperationsProvider;
    private final ProductMapper productMapper;
    private final CategoryMapper categoryMapper;
    private final QueryRouter queryRouter;
    private final QueryParser queryParser;
    private final QueryExpander queryExpander;
    private final ChineseTokenizer chineseTokenizer;

    @Override
    public SearchResponse search(SearchRequest request) {
        long start = System.currentTimeMillis();
        String rawQuery = request.getQuery();
        int page = request.getPage() != null ? request.getPage() : 1;
        int size = request.getSize() != null ? request.getSize() : 20;

        try {
            // 1. 意图路由
            queryRouter.route(rawQuery);

            // 2. 解析查询条件
            ParsedQuery parsed = queryParser.parse(rawQuery);

            // 3. 知识库扩展
            List<String> expandedTerms = queryExpander.expand(rawQuery);

            // 4. Java 端 Ansj 分词
            String keyword = parsed.getKeyword() != null ? parsed.getKeyword() : rawQuery;
            String[] tokens = chineseTokenizer.tokenizeArray(keyword);

            // 5. 改用 NativeQueryBuilder + QueryBuilders
            BoolQuery.Builder boolBuilder = new BoolQuery.Builder();
            boolBuilder.filter(QueryBuilders.term(t -> t.field("available").value(true)));

            // BUG-005 修复：合并 Ansj tokens 和知识库扩展词（去重）
            java.util.LinkedHashSet<String> tokenSet = new java.util.LinkedHashSet<>();
            if (tokens != null) tokenSet.addAll(Arrays.asList(tokens));
            if (expandedTerms != null) tokenSet.addAll(expandedTerms);
            String[] searchTokens = tokenSet.toArray(new String[0]);
            if (searchTokens.length == 0) {
                searchTokens = new String[]{rawQuery};
            }

            if (searchTokens.length > 0) {
                for (String token : searchTokens) {
                    boolBuilder.should(QueryBuilders.wildcard(w -> w
                            .field("nameTokens").value("*" + token + "*")));
                    boolBuilder.should(QueryBuilders.wildcard(w -> w
                            .field("categoryTokens").value("*" + token + "*")));
                    // BUG-007：同时搜描述分词（属性词，如"清淡少油素"）
                    boolBuilder.should(QueryBuilders.wildcard(w -> w
                            .field("propertyTokens").value("*" + token + "*")));
                }
                boolBuilder.minimumShouldMatch("1");
            } else {
                boolBuilder.must(QueryBuilders.wildcard(w -> w
                        .field("name").value("*" + (rawQuery == null ? "" : rawQuery) + "*")));
            }

            if (parsed.getMaxCalories() != null) {
                boolBuilder.filter(QueryBuilders.range(r -> r
                        .field("calories").lte(JsonData.of(parsed.getMaxCalories().doubleValue()))));
            }
            if (parsed.getMaxFat() != null) {
                boolBuilder.filter(QueryBuilders.range(r -> r
                        .field("fat").lte(JsonData.of(parsed.getMaxFat().doubleValue()))));
            }
            if (parsed.getMinProtein() != null) {
                boolBuilder.filter(QueryBuilders.range(r -> r
                        .field("protein").gte(JsonData.of(parsed.getMinProtein().doubleValue()))));
            }
            if (parsed.getCategory() != null && !parsed.getCategory().isEmpty()) {
                boolBuilder.filter(QueryBuilders.term(t -> t
                        .field("category").value(parsed.getCategory())));
            }

            Query esQuery = Query.of(q -> q.bool(boolBuilder.build()));

            NativeQuery nativeQuery = new NativeQueryBuilder()
                    .withQuery(esQuery)
                    .withPageable(PageRequest.of(page - 1, size))
                    .build();

            ElasticsearchOperations esOps = elasticsearchOperationsProvider.getIfAvailable();
            if (esOps == null) {
                throw new RuntimeException("ES 客户端不可用");
            }
            SearchHits<DishDocument> hits = esOps.search(nativeQuery, DishDocument.class);

            // ★ SEARCH-001-VO：构造 fid → DishDocument 映射
            Map<String, DishDocument> docMap = hits.stream()
                    .collect(Collectors.toMap(
                            h -> h.getContent().getId(),
                            h -> h.getContent(),
                            (a, b) -> a));

            List<String> fids = hits.stream()
                    .map(h -> h.getContent().getId())
                    .collect(Collectors.toList());

            List<Product> products = fids.isEmpty() ? Collections.emptyList()
                    : productMapper.selectList(new LambdaQueryWrapper<Product>()
                            .in(Product::getFid, fids)
                            .eq(Product::getStatus, 0));

            List<Product> orderedProducts = fids.stream()
                    .map(fid -> products.stream()
                            .filter(p -> fid.equals(p.getFid()))
                            .findFirst()
                            .orElse(null))
                    .filter(p -> p != null)
                    .collect(Collectors.toList());

            // ★ SEARCH-001-VO：批量查 Category（避免 N+1）
            List<Long> categoryIds = orderedProducts.stream()
                    .map(Product::getCategoryId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
            Map<Long, String> categoryMap = categoryIds.isEmpty()
                    ? Collections.emptyMap()
                    : categoryMapper.selectBatchIds(categoryIds).stream()
                            .collect(Collectors.toMap(
                                    Category::getId,
                                    Category::getName,
                                    (a, b) -> a));

            // ★ SEARCH-001-VO：Product → SearchProductVO 转换
            List<SearchProductVO> vos = orderedProducts.stream().map(p -> {
                DishDocument doc = docMap.get(p.getFid());
                BigDecimal price = p.getRealPrice() != null ? p.getRealPrice() : p.getNormPrice();
                return SearchProductVO.builder()
                        .id(p.getFid())
                        .name(p.getName())
                        .description(p.getDescription())
                        .cover(p.getCover())
                        .category(categoryMap.getOrDefault(p.getCategoryId(), ""))
                        .price(price)
                        .calories(doc != null ? doc.getCalories() : null)
                        .protein(doc != null ? doc.getProtein() : null)
                        .fat(doc != null ? doc.getFat() : null)
                        .available(p.getStatus() != null && p.getStatus() == 0)
                        .build();
            }).collect(Collectors.toList());

            long costMs = System.currentTimeMillis() - start;

            return SearchResponse.builder()
                    .products(vos)
                    .total((int) hits.getTotalHits())
                    .page(page)
                    .size(size)
                    .costMs((int) costMs)
                    .build();

        } catch (Exception e) {
            log.error("[搜索] ES 查询异常，降级到 MySQL: query={}, error={}",
                    rawQuery, e.getMessage(), e);
            return searchFallback(request);
        }
    }

    @Override
    public SearchResponse searchFallback(SearchRequest request) {
        long start = System.currentTimeMillis();
        String rawQuery = request.getQuery();
        int page = request.getPage() != null ? request.getPage() : 1;
        int size = request.getSize() != null ? request.getSize() : 20;

        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getStatus, 0);

        if (rawQuery != null && !rawQuery.trim().isEmpty()) {
            wrapper.like(Product::getName, rawQuery.trim());
        }

        wrapper.orderByDesc(Product::getSales);
        List<Product> all = productMapper.selectList(wrapper);
        int fromIndex = (page - 1) * size;
        int toIndex = Math.min(fromIndex + size, all.size());
        List<Product> paged = fromIndex < all.size() ? all.subList(fromIndex, toIndex) : List.of();

        // ★ SEARCH-001-VO：fallback 也用 VO（营养字段为 null）
        List<SearchProductVO> vos = paged.stream().map(p -> {
            BigDecimal price = p.getRealPrice() != null ? p.getRealPrice() : p.getNormPrice();
            return SearchProductVO.builder()
                    .id(p.getFid())
                    .name(p.getName())
                    .description(p.getDescription())
                    .cover(p.getCover())
                    .category("")  // fallback 不查 category
                    .price(price)
                    .calories(null)
                    .protein(null)
                    .fat(null)
                    .available(p.getStatus() != null && p.getStatus() == 0)
                    .build();
        }).collect(Collectors.toList());

        long costMs = System.currentTimeMillis() - start;

        return SearchResponse.builder()
                .products(vos)
                .total(all.size())
                .page(page)
                .size(size)
                .costMs((int) costMs)
                .build();
    }
}

