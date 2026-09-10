package com.sakana.web.controllers;

import com.sakana.dto.request.ProductReq;
import com.sakana.dto.request.ProductStatusReq;
import com.sakana.dto.request.StockAdjustReq;
import com.sakana.services.ProductService;
import com.sakana.web.vo.ProductPageResp;
import com.sakana.web.vo.ProductVO;
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
 * 管理后台 - 商品/库存管理
 */
@RestController
@RequestMapping("/api/v1/admin/products")
@RequiredArgsConstructor
@Tag(name = "管理后台-商品", description = "商品 CRUD、上下架、库存")
public class AdminProductController {

    private final ProductService productService;

    @GetMapping
    @Operation(summary = "商品分页（含上下架、库存、销量）")
    public R<ProductPageResp> getPage(
            @Parameter(description = "分类ID") @RequestParam(required = false) Long categoryId,
            @Parameter(description = "商品名称关键词") @RequestParam(required = false) String name,
            @Parameter(description = "状态：0上架 1下架（不传=全部）")
            @RequestParam(required = false) Integer status,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int size) {
        return R.ok(productService.adminGetPage(categoryId, name, status, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "商品详情（含下架）")
    public R<ProductVO> getDetail(@PathVariable Long id) {
        return R.ok(productService.adminGetDetail(id));
    }

    @PostMapping
    @Operation(summary = "新增商品")
    public R<Void> create(@Valid @RequestBody ProductReq req) {
        productService.adminCreate(req);
        return R.ok();
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改商品（库存不在此处改，走 /stock 接口）")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody ProductReq req) {
        productService.adminUpdate(id, req);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除商品（逻辑删除）")
    public R<Void> delete(@PathVariable Long id) {
        productService.adminDelete(id);
        return R.ok();
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "上下架 {status:0上架 1下架}")
    public R<Void> changeStatus(@PathVariable Long id, @Valid @RequestBody ProductStatusReq req) {
        productService.adminChangeStatus(id, req.getStatus());
        return R.ok();
    }

    @PutMapping("/{id}/stock")
    @Operation(summary = "调整库存（事务+CAS乐观锁，stock或delta二选一，结果不能为负）")
    public R<Void> adjustStock(@PathVariable Long id, @Valid @RequestBody StockAdjustReq req) {
        productService.adminAdjustStock(id, req);
        return R.ok();
    }
}

