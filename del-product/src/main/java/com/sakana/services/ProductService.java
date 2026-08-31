package com.sakana.services;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sakana.dao.entity.Product;
import com.sakana.dto.request.ProductReq;
import com.sakana.dto.request.StockAdjustReq;
import com.sakana.web.vo.ProductPageResp;
import com.sakana.web.vo.ProductVO;

import java.util.List;

/**
 * 商品服务接口
 */
public interface ProductService extends IService<Product> {

    /**
     * C 端：分页查询商品（仅上架）
     */
    ProductPageResp getPage(Long categoryId, String keyword, int page, int size);

    /**
     * C 端：商品详情（带缓存，仅上架可访问）
     */
    ProductVO getDetail(Long id);

    /**
     * C 端：热门商品
     */
    List<ProductVO> getHotProducts(int limit);

    // ==================== 管理后台 ====================

    /**
     * 管理后台：分页查询（可按状态过滤）
     *
     * @param status null=全部，0=上架，1=下架
     */
    ProductPageResp adminGetPage(Long categoryId, String keyword, Integer status, int page, int size);

    /**
     * 管理后台：商品详情（含下架，逻辑删除的看不到）
     */
    ProductVO adminGetDetail(Long id);

    /**
     * 管理后台：新增商品
     */
    void adminCreate(ProductReq req);

    /**
     * 管理后台：修改商品
     */
    void adminUpdate(Long id, ProductReq req);

    /**
     * 管理后台：删除商品（逻辑删除）
     */
    void adminDelete(Long id);

    /**
     * 管理后台：上下架
     */
    void adminChangeStatus(Long id, Integer status);

    /**
     * 管理后台：调整库存（事务 + CAS 乐观锁）
     */
    void adminAdjustStock(Long id, StockAdjustReq req);

    /**
     * 清除商品缓存
     */
    void evictCache(Long id);

    /**
     * 商品快照（供 del-order 进程内调用，省去 Feign 开销）
     * 不走 Redis 缓存，直接查库并映射为快照 VO。
     * 下架 / 不存在的商品抛 BizException(NOT_FOUND)。
     */
    com.sakana.feign.vo.ProductSnapshotVO getProductSnapshot(Long id);
}
