package com.sakana.services.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sakana.dao.entity.Category;
import com.sakana.dao.entity.Product;
import com.sakana.dao.mapper.CategoryMapper;
import com.sakana.dao.mapper.ProductMapper;
import com.sakana.dto.request.ProductReq;
import com.sakana.dto.request.StockAdjustReq;
import com.sakana.enums.ProductErrorCode;
import com.sakana.exceptions.BizException;
import com.sakana.services.ProductService;
import com.sakana.utils.SnowflakeIdGenerator;
import com.sakana.web.vo.ProductPageResp;
import com.sakana.web.vo.ProductVO;
import com.sakana.feign.vo.ProductSnapshotVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 商品服务实现
 */
@Service
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductServiceImpl.class);

    private static final String CACHE_KEY_PREFIX = "del-product:product:detail:";
    private static final long CACHE_TTL_MINUTES = 30;

    private final RedisTemplate<String, Object> redisTemplate;
    private final CategoryMapper categoryMapper;
    private final ProductMapper productMapper;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    public ProductServiceImpl(RedisTemplate<String, Object> redisTemplate,
                              CategoryMapper categoryMapper,
                              ProductMapper productMapper,
                              SnowflakeIdGenerator snowflakeIdGenerator) {
        this.redisTemplate = redisTemplate;
        this.categoryMapper = categoryMapper;
        this.productMapper = productMapper;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
    }

    // ==================== C 端 ====================

    @Override
    public ProductPageResp getPage(Long categoryId, String keyword, int page, int size) {
        Page<Product> pageParam = new Page<>(page, size);
        com.baomidou.mybatisplus.core.metadata.OrderItem.desc("sales");

        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getStatus, 0)
               .eq(categoryId != null, Product::getCategoryId, categoryId)
               .like(keyword != null && !keyword.isBlank(), Product::getName, keyword);

        com.baomidou.mybatisplus.core.metadata.IPage<Product> pageResult = page(pageParam, wrapper);

        Map<Long, String> categoryNameMap = getCategoryNameMap();

        ProductPageResp resp = new ProductPageResp();
        resp.setTotal(pageResult.getTotal());
        resp.setPage((long) pageResult.getCurrent());
        resp.setSize((long) pageResult.getSize());
        resp.setRecords(pageResult.getRecords().stream()
                .map(p -> toVO(p, categoryNameMap.get(p.getCategoryId())))
                .collect(Collectors.toList()));
        return resp;
    }

    @Override
    public ProductVO getDetail(Long id) {
        String cacheKey = CACHE_KEY_PREFIX + id;

        ProductVO cached = (ProductVO) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.debug("[商品缓存] 命中: id={}", id);
            return cached;
        }

        Product product = getById(id);
        if (product == null || product.getIsDeleted() == 1) {
            throw new BizException(ProductErrorCode.NOT_FOUND, id);
        }
        if (product.getStatus() == 1) {
            throw new BizException(ProductErrorCode.PRODUCT_OFF_SHELF);
        }

        String categoryName = null;
        if (product.getCategoryId() != null) {
            Category category = categoryMapper.selectById(product.getCategoryId());
            if (category != null) {
                categoryName = category.getName();
            }
        }

        ProductVO vo = toVO(product, categoryName);

        redisTemplate.opsForValue().set(cacheKey, vo, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        log.debug("[商品缓存] 写入: id={}, ttl={}min", id, CACHE_TTL_MINUTES);

        return vo;
    }

    @Override
    public List<ProductVO> getHotProducts(int limit) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getStatus, 0)
               .orderByDesc(Product::getSales)
               .last("LIMIT " + limit);

        Map<Long, String> categoryNameMap = getCategoryNameMap();

        return list(wrapper).stream()
                .map(p -> toVO(p, categoryNameMap.get(p.getCategoryId())))
                .collect(Collectors.toList());
    }

    // ==================== 管理后台 ====================

    @Override
    public ProductPageResp adminGetPage(Long categoryId, String keyword, Integer status,
                                        int page, int size) {
        Page<Product> pageParam = new Page<>(page, size);
        com.baomidou.mybatisplus.core.metadata.OrderItem.desc("createTime");

        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(categoryId != null, Product::getCategoryId, categoryId)
               .eq(status != null, Product::getStatus, status)
               .like(keyword != null && !keyword.isBlank(), Product::getName, keyword);

        com.baomidou.mybatisplus.core.metadata.IPage<Product> pageResult = page(pageParam, wrapper);

        Map<Long, String> categoryNameMap = getCategoryNameMap();

        ProductPageResp resp = new ProductPageResp();
        resp.setTotal(pageResult.getTotal());
        resp.setPage((long) pageResult.getCurrent());
        resp.setSize((long) pageResult.getSize());
        resp.setRecords(pageResult.getRecords().stream()
                .map(p -> toVO(p, categoryNameMap.get(p.getCategoryId())))
                .collect(Collectors.toList()));
        return resp;
    }

    @Override
    public ProductVO adminGetDetail(Long id) {
        Product product = getById(id);
        if (product == null || product.getIsDeleted() == 1) {
            throw new BizException(ProductErrorCode.NOT_FOUND, id);
        }
        String categoryName = null;
        if (product.getCategoryId() != null) {
            Category category = categoryMapper.selectById(product.getCategoryId());
            if (category != null) {
                categoryName = category.getName();
            }
        }
        return toVO(product, categoryName);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminCreate(ProductReq req) {
        Category category = categoryMapper.selectById(req.getCategoryId());
        if (category == null || category.getIsDeleted() == 1) {
            throw new BizException(ProductErrorCode.CATEGORY_NOT_FOUND);
        }

        Product product = new Product();
        BeanUtils.copyProperties(req, product);
        // 动态生成 fid（Snowflake）
        product.setFid(String.valueOf(snowflakeIdGenerator.nextId()));
        product.setSales(0);
        save(product);
        log.info("[商品创建] id={}, fid={}, name={}, stock={}",
                product.getId(), product.getFid(), product.getName(), product.getStock());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminUpdate(Long id, ProductReq req) {
        Product exist = getById(id);
        if (exist == null || exist.getIsDeleted() == 1) {
            throw new BizException(ProductErrorCode.NOT_FOUND, id);
        }

        Category category = categoryMapper.selectById(req.getCategoryId());
        if (category == null || category.getIsDeleted() == 1) {
            throw new BizException(ProductErrorCode.CATEGORY_NOT_FOUND);
        }

        BeanUtils.copyProperties(req, exist, "stock", "sales", "fid");
        updateById(exist);
        evictCache(id);
        log.info("[商品修改] id={}, fid={}, name={}", id, exist.getFid(), req.getName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminDelete(Long id) {
        Product exist = getById(id);
        if (exist == null || exist.getIsDeleted() == 1) {
            throw new BizException(ProductErrorCode.NOT_FOUND, id);
        }
        removeById(id);
        evictCache(id);
        log.info("[商品删除] id={}, fid={}", id, exist.getFid());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminChangeStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException(ProductErrorCode.STATUS_INVALID, status, status);
        }
        Product exist = getById(id);
        if (exist == null || exist.getIsDeleted() == 1) {
            throw new BizException(ProductErrorCode.NOT_FOUND, id);
        }
        exist.setStatus(status);
        updateById(exist);
        evictCache(id);
        log.info("[商品{}] id={}", status == 0 ? "上架" : "下架", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminAdjustStock(Long id, StockAdjustReq req) {
        Product product = getById(id);
        if (product == null || product.getIsDeleted() == 1) {
            throw new BizException(ProductErrorCode.NOT_FOUND, id);
        }

        int currentStock = product.getStock();
        int newStock;

        if (req.getStock() != null) {
            newStock = req.getStock();
        } else if (req.getDelta() != null) {
            newStock = currentStock + req.getDelta();
        } else {
            throw new BizException(ProductErrorCode.STOCK_INVALID);
        }

        if (newStock < 0) {
            throw new BizException(ProductErrorCode.STOCK_INVALID,
                    "库存值非法，调整后结果为负数（当前=" + currentStock + "）");
        }

        int affected;
        if (req.getStock() != null) {
            affected = productMapper.setStockIfMatch(id, currentStock, newStock);
        } else {
            affected = productMapper.adjustStockIfMatch(id, currentStock, req.getDelta());
        }

        if (affected == 0) {
            log.warn("[库存调整CAS失败] 商品库存已被其他事务修改: id={}, expected={}, new={}",
                    id, currentStock, newStock);
            throw new BizException(ProductErrorCode.STOCK_INVALID, "库存已被其他管理员修改，请重试");
        }

        evictCache(id);
        log.info("[库存调整] id={}, from={}, to={}, mode={}",
                id, currentStock, newStock,
                req.getStock() != null ? "set" : "delta(" + req.getDelta() + ")");
    }

    @Override
    public void evictCache(Long id) {
        String cacheKey = CACHE_KEY_PREFIX + id;
        redisTemplate.delete(cacheKey);
        log.debug("[商品缓存] 清除: id={}", id);
    }

    @Override
    public ProductSnapshotVO getProductSnapshot(Long id) {
        Product product = getById(id);
        if (product == null || product.getIsDeleted() == 1) {
            throw new BizException(ProductErrorCode.NOT_FOUND, id);
        }
        if (product.getStatus() == 1) {
            throw new BizException(ProductErrorCode.PRODUCT_OFF_SHELF);
        }
        ProductSnapshotVO vo = new ProductSnapshotVO();
        vo.setId(product.getId());
        vo.setCategoryId(product.getCategoryId());
        vo.setName(product.getName());
        vo.setCover(product.getCover());
        vo.setNormPrice(product.getNormPrice());
        vo.setRealPrice(product.getRealPrice());
        vo.setStock(product.getStock());
        vo.setSales(product.getSales());
        vo.setStatus(product.getStatus());
        return vo;
    }

    // ==================== 私有 ====================

    private ProductVO toVO(Product product, String categoryName) {
        ProductVO vo = new ProductVO();
        BeanUtils.copyProperties(product, vo);
        vo.setCategoryName(categoryName);
        vo.setLikeCount(0);
        vo.setDislikeCount(0);
        return vo;
    }

    private Map<Long, String> getCategoryNameMap() {
        List<Category> categories = categoryMapper.selectList(null);
        return categories.stream()
                .collect(Collectors.toMap(Category::getId, Category::getName, (a, b) -> a));
    }
}
