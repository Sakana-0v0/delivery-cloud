package com.sakana.search.service.impl;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.sakana.dao.entity.Category;
import com.sakana.dao.entity.Product;
import com.sakana.dao.mapper.CategoryMapper;
import com.sakana.dao.mapper.ProductMapper;
import com.sakana.search.dto.SearchRequest;
import com.sakana.search.dto.SearchResponse;
import com.sakana.search.service.*;
import com.sakana.search.tokenizer.ChineseTokenizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * SearchServiceImpl 关键方法签名 + Sentinel 集成 测试。
 *
 * <p>不直接调用 search()（需要完整 ES mock 链路），主要验证：
 * <ul>
 *   <li>@SentinelResource 注解存在且 value=dishSearch</li>
 *   <li>searchBlockHandler / searchFallbackWithEx 方法签名正确（被 AOP 反射调用）</li>
 *   <li>searchBlockHandler 复用 searchFallback（不直接实现降级逻辑）</li>
 *   <li>searchFallbackWithEx 在传入异常时不重新抛出</li>
 *   <li>SearchServiceImpl.RESOURCE 常量值正确</li>
 * </ul>
 */
@DisplayName("SearchServiceImpl Sentinel 集成测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SearchServiceImplTest {

    @Mock private ObjectProvider<org.springframework.data.elasticsearch.core.ElasticsearchOperations> esOpsProvider;
    @Mock private ProductMapper productMapper;
    @Mock private CategoryMapper categoryMapper;
    @Mock private QueryRouter queryRouter;
    @Mock private QueryParser queryParser;
    @Mock private QueryExpander queryExpander;
    @Mock private ChineseTokenizer chineseTokenizer;

    private SearchServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SearchServiceImpl(
                esOpsProvider, productMapper, categoryMapper,
                queryRouter, queryParser, queryExpander, chineseTokenizer
        );
        // mock MySQL fallback 返回 1 个 product
        Product p = new Product();
        p.setFid("test-fid-001");
        p.setName("测试商品");
        p.setNormPrice(new BigDecimal("10.0"));
        p.setStatus(0);
        when(productMapper.selectList(any())).thenReturn(Collections.singletonList(p));
        when(chineseTokenizer.tokenizeArray(anyString())).thenReturn(new String[]{"test"});
    }

    @Test
    @DisplayName("@SentinelResource 注解存在且 value=dishSearch")
    void testSentinelAnnotationPresent() throws NoSuchMethodException {
        Method searchMethod = SearchServiceImpl.class.getMethod("search", SearchRequest.class);
        SentinelResource annotation = searchMethod.getAnnotation(SentinelResource.class);

        assertNotNull(annotation, "search() 必须有 @SentinelResource 注解");
        assertEquals("dishSearch", annotation.value());
        assertEquals("searchBlockHandler", annotation.blockHandler());
        assertEquals("searchFallbackWithEx", annotation.fallback());
    }

    @Test
    @DisplayName("searchBlockHandler 方法签名：同返回值 + 同入参 + 末尾 BlockException")
    void testBlockHandlerSignature() throws NoSuchMethodException {
        Method method = SearchServiceImpl.class.getMethod("searchBlockHandler", SearchRequest.class, BlockException.class);
        assertEquals(SearchResponse.class, method.getReturnType());
        Parameter[] params = method.getParameters();
        assertEquals(2, params.length);
        assertEquals(SearchRequest.class, params[0].getType());
        assertEquals(BlockException.class, params[1].getType());
    }

    @Test
    @DisplayName("searchFallbackWithEx 方法签名：同返回值 + 同入参 + 末尾 Throwable")
    void testFallbackWithExSignature() throws NoSuchMethodException {
        Method method = SearchServiceImpl.class.getMethod("searchFallbackWithEx", SearchRequest.class, Throwable.class);
        assertEquals(SearchResponse.class, method.getReturnType());
        Parameter[] params = method.getParameters();
        assertEquals(2, params.length);
        assertEquals(SearchRequest.class, params[0].getType());
        assertEquals(Throwable.class, params[1].getType());
    }

    @Test
    @DisplayName("searchBlockHandler 复用 searchFallback 实现降级逻辑")
    void testBlockHandlerUsesFallback() {
        SearchRequest req = new SearchRequest();
        req.setQuery("test");
        req.setPage(1);
        req.setSize(20);

        // 用 mock BlockException 避免 NPE（实现会调用 e.getClass()/e.getRule()）
        BlockException mockException = mock(BlockException.class);
        SearchResponse resp = service.searchBlockHandler(req, mockException);
        assertNotNull(resp);
        assertNotNull(resp.getProducts());
        assertFalse(resp.getProducts().isEmpty(), "blockHandler 应通过 fallback 返回 MySQL 数据");
        assertEquals("测试商品", resp.getProducts().get(0).getName());
    }

    @Test
    @DisplayName("searchFallbackWithEx 容忍 null 异常（不被吞也安全）")
    void testFallbackWithEx_toleratesNullException() {
        SearchRequest req = new SearchRequest();
        req.setQuery("test");

        // 传入 null Throwable，不应 NPE
        SearchResponse resp = service.searchFallbackWithEx(req, null);
        assertNotNull(resp);
        assertNotNull(resp.getProducts());
    }

    @Test
    @DisplayName("searchFallbackWithEx 传入真实异常也不抛")
    void testFallbackWithEx_realException() {
        SearchRequest req = new SearchRequest();
        req.setQuery("test");

        SearchResponse resp = service.searchFallbackWithEx(req, new RuntimeException("ES timeout"));
        assertNotNull(resp);
    }

    @Test
    @DisplayName("RESOURCE 常量值正确")
    void testResourceConstant() {
        assertEquals("dishSearch", SearchServiceImpl.RESOURCE);
    }

    @Test
    @DisplayName("searchFallback(MySQL LIKE) 直接调用也可用")
    void testFallbackDirectCall() {
        SearchRequest req = new SearchRequest();
        req.setQuery("test");
        req.setPage(1);
        req.setSize(20);

        SearchResponse resp = service.searchFallback(req);
        assertNotNull(resp);
        assertNotNull(resp.getProducts());
    }
}
