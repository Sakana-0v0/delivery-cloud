package com.sakana.web.controllers;


import com.sakana.web.vo.R;
import com.sakana.services.CategoryService;
import com.sakana.web.vo.CategoryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商品分类接口
 */
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@Tag(name = "商品分类", description = "分类列表")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @Operation(summary = "分类列表", description = "获取所有启用的分类列表")
    public R<List<CategoryVO>> getList() {
        return R.ok(categoryService.getList());
    }
}