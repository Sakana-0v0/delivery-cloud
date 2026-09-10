package com.sakana.search.web.admin;

import com.sakana.search.dto.IndexTask;
import com.sakana.search.service.IndexingService;
import com.sakana.web.vo.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 管理端索引管理 API（#SEARCH-001）
 *
 * <p>路由：POST /api/v1/admin/index/rebuild
 * <p>功能：触发全量索引重建（同步/异步）
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/index")
@RequiredArgsConstructor
@Tag(name = "管理端索引管理", description = "索引重建和管理")
@SecurityRequirement(name = "admin-token")
public class AdminIndexController {

    private final IndexingService indexingService;

    /**
     * 触发全量索引重建（支持同步/异步）
     *
     * @param async 是否异步执行（默认 true）
     */
    @PostMapping(value = "/rebuild", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "重建索引", description = "全量扫描 t_product 表并重建 ES 索引，支持同步/异步模式")
    public R<Map<String, Object>> rebuildIndex(
            @RequestParam(defaultValue = "true") boolean async
    ) {
        log.info("[索引管理] 收到重建索引请求, async={}", async);

        if (async) {
            // 异步模式：立即返回
            indexingService.rebuildAllIndexAsync();
            return R.ok(Map.of(
                    "status", "started",
                    "message", "索引重建任务已提交，请等待完成"
            ));
        } else {
            // 同步模式：等待完成并返回真实结果（修复 BUG-004：返回 success/fail 计数）
            long start = System.currentTimeMillis();
            IndexTask task = indexingService.rebuildAll();
            long cost = System.currentTimeMillis() - start;
            Map<String, Object> result = new HashMap<>();
            result.put("status", "completed");
            result.put("message", "索引重建完成");
            result.put("costMs", cost);
            result.put("success", task.getSuccess());
            result.put("failed", task.getFailed());
            result.put("total", task.getTotal());
            result.put("taskStatus", task.getStatus());
            if (task.getErrorMsg() != null) {
                result.put("error", task.getErrorMsg());
            }
            log.info("[索引管理] 同步重建完成: total={}, success={}, failed={}, costMs={}",
                    task.getTotal(), task.getSuccess(), task.getFailed(), cost);
            return R.ok(result);
        }
    }

    /**
     * 查询索引状态
     */
    @GetMapping("/status")
    @Operation(summary = "索引状态", description = "查询当前 ES 索引状态")
    public R<Map<String, Object>> indexStatus() {
        // #BUG-009 修复：getLastTask() 已在 IndexingService 接口中
        // 不再需要 (IndexingServiceImpl) indexingService 强转（AOP 代理问题）
        IndexTask task = indexingService.getLastTask();
        Map<String, Object> result = new HashMap<>();
        result.put("indexName", "dish");
        result.put("taskStatus", task.getStatus());
        result.put("success", task.getSuccess());
        result.put("failed", task.getFailed());
        result.put("total", task.getTotal());
        return R.ok(result);
    }
}