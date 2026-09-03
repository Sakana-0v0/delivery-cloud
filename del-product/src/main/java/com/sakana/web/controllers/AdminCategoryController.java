package com.sakana.web.controllers;

import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sakana.dto.request.CategoryReq;
import com.sakana.services.CategoryService;
import com.sakana.web.vo.CategoryVO;
import com.sakana.web.vo.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台 - 分类管理
 */
@RestController
@RequestMapping("/api/v1/admin/categories")
@RequiredArgsConstructor
@Tag(name = "管理后台-分类", description = "分类 CRUD")
public class AdminCategoryController {

    private final CategoryService categoryService;

    @GetMapping("/all")
    @Operation(summary = "获取所有分类（全量，下拉框专用）")
    public R<List<CategoryVO>> getAll() {
        return R.ok(categoryService.adminGetAll());
    }

    @GetMapping
    @Operation(summary = "分类列表（管理后台，含禁用）")
    public R<IPage<CategoryVO>> getPage(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int size) {
        return R.ok(categoryService.adminGetPage(page, size));
    }

    @PostMapping
    @Operation(summary = "新增分类")
    public R<Void> create(@Valid @RequestBody CategoryReq req) {
        categoryService.adminCreate(req);
        return R.ok();
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改分类")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody CategoryReq req) {
        categoryService.adminUpdate(id, req);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除分类（前置校验：该分类下不能有商品）")
    public R<Void> delete(@PathVariable Long id) {
        categoryService.adminDelete(id);
        return R.ok();
    }
}
