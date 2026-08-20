package com.sakana.services;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sakana.dao.entity.Category;
import com.sakana.dto.request.CategoryReq;
import com.sakana.web.vo.CategoryVO;

import java.util.List;

/**
 * 商品分类服务接口
 */
public interface CategoryService extends IService<Category> {

    /**
     * 获取分类列表（仅启用状态，C 端）
     */
    List<CategoryVO> getList();

    /**
     * C 端分页
     */
    IPage<CategoryVO> getPage(int page, int size);

    // ==================== 管理后台 ====================

    /**
     * 管理后台分页（含禁用状态）
     */
    IPage<CategoryVO> adminGetPage(int page, int size);

    /**
     * 管理后台新增分类
     */
    void adminCreate(CategoryReq req);

    /**
     * 管理后台修改分类
     */
    void adminUpdate(Long id, CategoryReq req);

    /**
     * 管理后台删除分类（前置校验：分类下不能有商品）
     */
    void adminDelete(Long id);
}
