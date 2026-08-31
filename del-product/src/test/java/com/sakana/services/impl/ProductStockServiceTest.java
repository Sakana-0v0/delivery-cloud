package com.sakana.services.impl;

import com.sakana.dao.entity.Product;
import com.sakana.dao.mapper.CategoryMapper;
import com.sakana.dao.mapper.ProductMapper;
import com.sakana.dto.request.StockAdjustReq;
import com.sakana.enums.ProductErrorCode;
import com.sakana.exceptions.BizException;
import com.sakana.utils.SnowflakeIdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ProductServiceImpl 库存调整（CAS 乐观锁）测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductStockServiceTest {

    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private CategoryMapper categoryMapper;
    @Mock private ProductMapper productMapper;
    @Mock private SnowflakeIdGenerator snowflakeIdGenerator;

    private ProductServiceImpl service;

    private Product newProduct(Long id, int stock) {
        Product p = new Product();
        p.setId(id);
        p.setName("测试商品");
        p.setStock(stock);
        p.setStatus(0);
        p.setIsDeleted(0);
        p.setCategoryId(1L);
        return p;
    }

    private ProductServiceImpl newService() {
        ProductServiceImpl impl = new ProductServiceImpl(redisTemplate, categoryMapper, productMapper, snowflakeIdGenerator);
        try {
            Field baseMapperField = Class.forName("com.baomidou.mybatisplus.extension.service.impl.ServiceImpl")
                    .getDeclaredField("baseMapper");
            baseMapperField.setAccessible(true);
            baseMapperField.set(impl, productMapper);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return impl;
    }

    @BeforeEach
    void setUp() {
        service = newService();
    }

    @Test
    void adjustStock_setMode_casSucceeds() {
        Product p = newProduct(100L, 50);
        when(productMapper.selectById(100L)).thenReturn(p);
        when(productMapper.setStockIfMatch(eq(100L), eq(50), eq(80))).thenReturn(1);

        StockAdjustReq req = new StockAdjustReq();
        req.setStock(80);
        service.adminAdjustStock(100L, req);

        // service 不修改 in-memory Product 状态 — 只通过 CAS 更新 DB
        verify(productMapper).setStockIfMatch(100L, 50, 80);
        verify(productMapper, never()).adjustStockIfMatch(anyLong(), anyInt(), anyInt());
    }

    @Test
    void adjustStock_deltaMode_casSucceeds() {
        Product p = newProduct(100L, 50);
        when(productMapper.selectById(100L)).thenReturn(p);
        when(productMapper.adjustStockIfMatch(eq(100L), eq(50), eq(-10))).thenReturn(1);

        StockAdjustReq req = new StockAdjustReq();
        req.setDelta(-10);
        service.adminAdjustStock(100L, req);

        // service 不修改 in-memory Product 状态 — 只通过 CAS 更新 DB
        verify(productMapper).adjustStockIfMatch(100L, 50, -10);
    }

    @Test
    void adjustStock_casFailure_throws() {
        Product p = newProduct(100L, 50);
        when(productMapper.selectById(100L)).thenReturn(p);
        when(productMapper.setStockIfMatch(eq(100L), eq(50), eq(80))).thenReturn(0);

        StockAdjustReq req = new StockAdjustReq();
        req.setStock(80);
        BizException ex = assertThrows(BizException.class,
                () -> service.adminAdjustStock(100L, req));
        assertEquals(ProductErrorCode.STOCK_INVALID.getCode(), ex.getCode());
    }

    @Test
    void adjustStock_negativeResult_throws() {
        Product p = newProduct(100L, 5);
        when(productMapper.selectById(100L)).thenReturn(p);

        StockAdjustReq req = new StockAdjustReq();
        req.setDelta(-10);
        BizException ex = assertThrows(BizException.class,
                () -> service.adminAdjustStock(100L, req));
        assertEquals(ProductErrorCode.STOCK_INVALID.getCode(), ex.getCode());
    }

    @Test
    void adjustStock_bothStockAndDelta_throws() {
        Product p = newProduct(100L, 50);
        when(productMapper.selectById(100L)).thenReturn(p);

        StockAdjustReq req = new StockAdjustReq();
        req.setStock(80);
        req.setDelta(10);

        BizException ex = assertThrows(BizException.class,
                () -> service.adminAdjustStock(100L, req));
        assertEquals(ProductErrorCode.STOCK_INVALID.getCode(), ex.getCode());
    }

    @Test
    void adjustStock_neitherStockNorDelta_throws() {
        Product p = newProduct(100L, 50);
        when(productMapper.selectById(100L)).thenReturn(p);

        StockAdjustReq req = new StockAdjustReq();
        BizException ex = assertThrows(BizException.class,
                () -> service.adminAdjustStock(100L, req));
        assertEquals(ProductErrorCode.STOCK_INVALID.getCode(), ex.getCode());
    }

    @Test
    void adjustStock_productNotFound_throws() {
        when(productMapper.selectById(100L)).thenReturn(null);

        StockAdjustReq req = new StockAdjustReq();
        req.setStock(80);
        BizException ex = assertThrows(BizException.class,
                () -> service.adminAdjustStock(100L, req));
        assertEquals(ProductErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void adjustStock_logicallyDeleted_throws() {
        Product p = newProduct(100L, 50);
        p.setIsDeleted(1);
        when(productMapper.selectById(100L)).thenReturn(p);

        StockAdjustReq req = new StockAdjustReq();
        req.setStock(80);
        BizException ex = assertThrows(BizException.class,
                () -> service.adminAdjustStock(100L, req));
        assertEquals(ProductErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void adjustStock_success_clearsCache() {
        Product p = newProduct(100L, 50);
        when(productMapper.selectById(100L)).thenReturn(p);
        when(productMapper.setStockIfMatch(any(), anyInt(), anyInt())).thenReturn(1);

        StockAdjustReq req = new StockAdjustReq();
        req.setStock(80);
        service.adminAdjustStock(100L, req);

        verify(redisTemplate).delete("del-product:product:detail:100");
    }

    @Test
    void adjustStock_casFail_doesNotClearCache() {
        Product p = newProduct(100L, 50);
        when(productMapper.selectById(100L)).thenReturn(p);
        when(productMapper.setStockIfMatch(any(), anyInt(), anyInt())).thenReturn(0);

        StockAdjustReq req = new StockAdjustReq();
        req.setStock(80);
        assertThrows(BizException.class,
                () -> service.adminAdjustStock(100L, req));
        verify(redisTemplate, never()).delete(anyString());
    }
}
