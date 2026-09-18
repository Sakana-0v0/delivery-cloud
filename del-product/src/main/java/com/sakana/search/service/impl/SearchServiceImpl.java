package com.sakana.search.service.impl;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.json.JsonData;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
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
import com.sakana.metrics.SearchMetrics;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 搜索服务实现（#SEARCH-001 / #SEARCH-001-VO / #SEARCH-SENTINEL）
 *
 * <p>★ P0-Sentinel 接入：
 * <ul>
 *   <li>@SentinelResource("dishSearch") 给搜索入口挂限流/熔断规则</li>
 *   <li>Sentinel 阻断（限流/熔断）→ searchBlockHandler → MySQL 兜底</li>
 *   <li>业务异常（ES 超时、连接失败）→ searchFallbackWithEx → MySQL 兜底</li>
 *   <li>规则来源 Nacos：sentinel-flow-rules-delproduct / sentinel-degrade-rules-delproduct</li>
 *   <li>Nacos 不可用时，启动装载本地默认规则（兜底中的兜底）</li>
 * </ul>
 *
 * <p>ElasticsearchOperations 用 ObjectProvider 包装，避免 ES 不可用时启动失败。
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
    private final SearchMetrics searchMetrics;

    /** Sentinel 资源名（与 @SentinelResource.value 一致） */
    public static final String RESOURCE = "dishSearch";

    /**
     * 启动兜底：Nacos 规则加载失败/未配置时，本地默认规则立即生效。
     * Nacos datasource 加载完成后会自动覆盖本地规则（以 Nacos 为准）。
     */
    @PostConstruct
    public void initDefaultRules() {
        List<FlowRule> flowRules = new ArrayList<>();
        FlowRule flow = new FlowRule(RESOURCE);
        flow.setGrade(RuleConstant.FLOW_GRADE_QPS);
        flow.setCount(100);
        flow.setLimitApp("default");
        flowRules.add(flow);
        FlowRuleManager.loadRules(flowRules);

        List<DegradeRule> degradeRules = new ArrayList<>();
        DegradeRule degrade = new DegradeRule(RESOURCE)
                .setCount(1500)
                .setGrade(RuleConstant.DEGRADE_GRADE_RT)
                .setMinRequestAmount(5)
                .setStatIntervalMs(10000)
                .setTimeWindow(30);
        degradeRules.add(degrade);
        DegradeRuleManager.loadRules(degradeRules);

        log.info("[SearchServiceImpl] 兜底 Sentinel 规则已装载: resource={}, qps=100, rt=1500ms, timeWindow=30s", RESOURCE);
    }

    /**
     * ★ 主搜索入口：Sentinel 资源点
     *
     * <p>注意：不要再手动 SphU.entry，@SentinelResource AOP 已自动接管。
     * 业务异常抛出即可，由 fallback 路由。
     */
    @Override
    @SentinelResource(
            value = RESOURCE,
            blockHandler = "searchBlockHandler",
            fallback = "searchFallbackWithEx"
    )
    public SearchResponse search(SearchRequest request) {
        long startMs = System.currentTimeMillis();
        String rawQuery = request.getQuery();
        int page = request.getPage() != null ? request.getPage() : 1;
        int size = request.getSize() != null ? request.getSize() : 20;

        // 1. 意图路由
        queryRouter.route(rawQuery);
        // 2. 解析查询条件
        ParsedQuery parsed = queryParser.parse(rawQuery);
        // 3. 知识库扩展
        List<String> expandedTerms = queryExpander.expand(rawQuery);
        // 4. Java 端 Ansj 分词
        String keyword = parsed.getKeyword() != null ? parsed.getKeyword() : rawQuery;
        String[] tokens = chineseTokenizer.tokenizeArray(keyword);

        // 5. 构建 ES 查询
        BoolQuery.Builder boolBuilder = new BoolQuery.Builder();
        boolBuilder.filter(QueryBuilders.term(t -> t.field("available").value(true)));

        LinkedHashSet<String> tokenSet = new LinkedHashSet<>();
        if (tokens != null) tokenSet.addAll(Arrays.asList(tokens));
        if (expandedTerms != null) tokenSet.addAll(expandedTerms);
        String[] searchTokens = tokenSet.toArray(new String[0]);
        if (searchTokens.length == 0) {
            searchTokens = new String[]{rawQuery};
        }
        for (String token : searchTokens) {
            boolBuilder.should(QueryBuilders.wildcard(w -> w.field("nameTokens").value("*" + token + "*")));
            boolBuilder.should(QueryBuilders.wildcard(w -> w.field("categoryTokens").value("*" + token + "*")));
            boolBuilder.should(QueryBuilders.wildcard(w -> w.field("propertyTokens").value("*" + token + "*")));
        }
        boolBuilder.minimumShouldMatch("1");

        if (parsed.getMaxCalories() != null) {
            boolBuilder.filter(QueryBuilders.range(r -> r
                    .field("calories").lte(JsonData.of(parsed.getMaxCalories().doubleValue()))));
        }
        if (parsed.getMaxFat() != null) {
            boolBuilder.filter(QueryBuilders.range(r -> r
                    .field("fat").lte(JsonData.of(parsed.getMaxFat().doubleValue()))));
        }

        ElasticsearchOperations esOps = elasticsearchOperationsProvider.getIfAvailable();
        if (esOps == null) {
            throw new IllegalStateException("ElasticsearchOperations 不可用");
        }
        NativeQueryBuilder nqBuilder = new NativeQueryBuilder().withQuery(q -> q.bool(boolBuilder.build()));
        nqBuilder.withPageable(PageRequest.of(page - 1, size));
        SearchHits<DishDocument> hits = esOps.search(nqBuilder.build(), DishDocument.class);

        List<String> fids = hits.stream().map(h -> h.getContent().getId()).collect(Collectors.toList());
        log.debug("[搜索] ES 命中 {} 条，fids={}", fids.size(), fids);

        List<Product> products = fids.isEmpty() ? Collections.emptyList()
                : productMapper.selectList(new LambdaQueryWrapper<Product>()
                        .in(Product::getFid, fids)
                        .eq(Product::getStatus, 0));
        List<Product> orderedProducts = fids.stream()
                .map(fid -> products.stream().filter(p -> fid.equals(p.getFid())).findFirst().orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        List<Long> categoryIds = orderedProducts.stream()
                .map(Product::getCategoryId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, String> categoryMap = categoryIds.isEmpty() ? Collections.emptyMap()
                : categoryMapper.selectBatchIds(categoryIds).stream()
                        .collect(Collectors.toMap(Category::getId, Category::getName, (a, b) -> a));

        Map<String, DishDocument> docMap = hits.stream()
                .collect(Collectors.toMap(h -> h.getContent().getId(), h -> h.getContent(), (a, b) -> a));

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

        long costMs = System.currentTimeMillis() - startMs;
        log.info("[搜索] ES 成功: query={}, total={}, costMs={}", rawQuery, hits.getTotalHits(), costMs);

        return SearchResponse.builder()
                .products(vos)
                .total((int) hits.getTotalHits())
                .page(page)
                .size(size)
                .costMs((int) costMs)
                .build();
    }

    /**
     * Sentinel blockHandler：限流/熔断触发时由 AOP 调用。
     * 签名要求：同返回值 + 相同入参 + 末尾追加 BlockException。
     */
    @SuppressWarnings("unused")
    public SearchResponse searchBlockHandler(SearchRequest request, BlockException e) {
        log.warn("[搜索] Sentinel 阻断（{}）: query={}, rule={}",
                e.getClass().getSimpleName(), request.getQuery(), e.getRule());
        // P3-MONITORING：Sentinel 触发熔断/限流
        searchMetrics.recordCall(SearchMetrics.OUTCOME_BLOCK, 0);
        return searchFallback(request);
    }

    /**
     * Sentinel fallback：业务异常（ES 不可用、超时、Mapper 异常）由 AOP 调用。
     * 签名要求：同返回值 + 相同入参 + 末尾追加 Throwable。
     */
    @SuppressWarnings("unused")
    public SearchResponse searchFallbackWithEx(SearchRequest request, Throwable e) {
        log.warn("[搜索] 业务异常，降级到 MySQL: query={}, error={}",
                request.getQuery(), e == null ? "null" : e.getMessage());
        // P3-MONITORING：业务异常已计入 ERROR；fallback 自身不重复计数（避免双计）
        return searchFallback(request);
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

        List<SearchProductVO> vos = paged.stream().map(p -> {
            BigDecimal price = p.getRealPrice() != null ? p.getRealPrice() : p.getNormPrice();
            return SearchProductVO.builder()
                    .id(p.getFid())
                    .name(p.getName())
                    .description(p.getDescription())
                    .cover(p.getCover())
                    .category("")
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