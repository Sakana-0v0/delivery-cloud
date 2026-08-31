package com.sakana.web.controllers;

import com.sakana.feign.vo.ProductSnapshotVO;
import com.sakana.services.ProductService;
import com.sakana.web.vo.ProductPageResp;
import com.sakana.web.vo.ProductVO;
import com.sakana.web.vo.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商品接口（C 端）
 */
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "商品", description = "商品列表、详情、热门")
public class ProductController {

    private final ProductService productService;

    @GetMapping
    @Operation(summary = "商品分页", description = "分页查询商品列表，支持分类筛选和关键词搜索")
    public R<ProductPageResp> getPage(
            @Parameter(description = "分类ID") @RequestParam(required = false) Long categoryId,
            @Parameter(description = "关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int size) {
        return R.ok(productService.getPage(categoryId, keyword, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "商品详情", description = "获取商品详细信息，带缓存")
    public R<ProductVO> getDetail(@PathVariable Long id) {
        return R.ok(productService.getDetail(id));
    }

    @GetMapping("/{id}/snapshot")
    @Operation(summary = "商品快照（供内部服务调用）", description = "返回精简商品信息，不走缓存")
    public R<ProductSnapshotVO> getSnapshot(@PathVariable Long id) {
        return R.ok(productService.getProductSnapshot(id));
    }

    @GetMapping("/hot")
    @Operation(summary = "热门商品", description = "获取销量最高的N个商品")
    public R<List<ProductVO>> getHot(
            @Parameter(description = "返回数量，默认10") @RequestParam(defaultValue = "10") int limit) {
        return R.ok(productService.getHotProducts(limit));
    }
}
