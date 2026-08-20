package com.sakana.services.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sakana.dao.entity.Category;
import com.sakana.dao.mapper.CategoryMapper;
import com.sakana.dao.mapper.ProductMapper;
import com.sakana.dto.request.CategoryReq;
import com.sakana.enums.ProductErrorCode;
import com.sakana.exceptions.BizException;
import com.sakana.services.CategoryService;
import com.sakana.web.vo.CategoryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 商品分类服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements CategoryService {

    private final ProductMapper productMapper;

    // ==================== C 端 ====================

    @Override
    public List<CategoryVO> getList() {
        LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Category::getStatus, 0)
               .orderByAsc(Category::getSort);

        return list(wrapper).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public IPage<CategoryVO> getPage(int page, int size) {
        Page<Category> pageParam = new Page<>(page, size);
        pageParam.addOrder(OrderItem.asc("sort"));

        LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(Category::getSort);

        Page<Category> pageResult = page(pageParam, wrapper);
        return pageResult.convert(this::toVO);
    }

    // ==================== 管理后台 ====================

    @Override
    public IPage<CategoryVO> adminGetPage(int page, int size) {
        Page<Category> pageParam = new Page<>(page, size);
        pageParam.addOrder(OrderItem.asc("sort"));
        Page<Category> pageResult = page(pageParam);
        return pageResult.convert(this::toVO);
    }

    @Override
    public void adminCreate(CategoryReq req) {
        Category category = new Category();
        BeanUtils.copyProperties(req, category);
        save(category);
        log.info("[分类创建] id={}, name={}", category.getId(), category.getName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminUpdate(Long id, CategoryReq req) {
        Category exist = getById(id);
        if (exist == null || exist.getIsDeleted() == 1) {
            throw new BizException(ProductErrorCode.CATEGORY_NOT_FOUND);
        }
        BeanUtils.copyProperties(req, exist);
        updateById(exist);
        log.info("[分类修改] id={}, name={}", id, req.getName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminDelete(Long id) {
        Category exist = getById(id);
        if (exist == null || exist.getIsDeleted() == 1) {
            throw new BizException(ProductErrorCode.CATEGORY_NOT_FOUND);
        }

        long productCount = productMapper.countByCategoryId(id);
        if (productCount > 0) {
            log.warn("[分类删除失败] 分类下仍有 {} 个商品: id={}", productCount, id);
            throw new BizException(ProductErrorCode.CATEGORY_HAS_PRODUCT);
        }

        removeById(id);
        log.info("[分类删除] id={}, name={}", id, exist.getName());
    }

    private CategoryVO toVO(Category category) {
        CategoryVO vo = new CategoryVO();
        BeanUtils.copyProperties(category, vo);
        return vo;
    }
}
