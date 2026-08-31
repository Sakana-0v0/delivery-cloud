package com.sakana.web.controllers.stats;

import com.sakana.stats.ProductStatsService;
import com.sakana.stats.dto.HotProductVO;
import com.sakana.web.vo.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商品统计内部 API（仅内网可达，供 del-stats 聚合服务调用）
 */
@RestController
@RequestMapping("/internal/stats")
@RequiredArgsConstructor
@Tag(name = "商品统计-内部", description = "供 del-stats 聚合服务调用的商品统计接口")
public class ProductInternalStatsController {

    private final ProductStatsService productStatsService;

    @GetMapping("/hot-products")
    @Operation(summary = "获取热卖商品列表（供聚合服务调用）")
    public R<List<HotProductVO>> getHotProducts(
            @Parameter(description = "返回数量，默认10，最大100")
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "最小值为1")
            @Max(value = 100, message = "最大值为100") int limit) {
        return R.ok(productStatsService.getHotProducts(limit));
    }
}