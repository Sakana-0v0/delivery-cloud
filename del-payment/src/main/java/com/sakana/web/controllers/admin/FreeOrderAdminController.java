package com.sakana.web.controllers.admin;

import com.sakana.security.SecurityUtil;
import com.sakana.services.FreeOrderActivityService;
import com.sakana.web.vo.FreeOrderActivityVO;
import com.sakana.web.vo.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/free-orders")
@RequiredArgsConstructor
@Tag(name = "免单活动管理", description = "管理员创建/发布/查询免单活动")
public class FreeOrderAdminController {

    private final FreeOrderActivityService freeOrderActivityService;

    /**
     * ★ BUG-006 修复：兼容多种时间格式
     * - "2026-09-10 20:45:00"（Element Plus el-date-picker 默认）
     * - "2026-09-10T20:45:00"（ISO 8601）
     */
    private static final DateTimeFormatter[] DATETIME_FORMATTERS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    };

    private LocalDateTime parseDateTime(String s) {
        if (s == null || s.isBlank()) return null;
        for (DateTimeFormatter f : DATETIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(s, f);
            } catch (Exception ignored) {}
        }
        throw new IllegalArgumentException("时间格式错误: " + s + "（支持 yyyy-MM-dd HH:mm:ss 或 ISO 8601）");
    }

    @PostMapping
    @Operation(summary = "创建免单活动")
    public R<Long> createActivity(@RequestBody Map<String, Object> req) {
        String name = (String) req.get("name");
        // ★ BUG-006：用 parseDateTime 替代 LocalDateTime.parse
        LocalDateTime startTime = parseDateTime((String) req.get("startTime"));
        LocalDateTime grabTime = parseDateTime((String) req.get("grabTime"));
        LocalDateTime endTime = parseDateTime((String) req.get("endTime"));
        Integer totalQuota = Integer.valueOf(req.get("totalQuota").toString());
        BigDecimal maxFreeAmount = new BigDecimal(req.get("maxFreeAmount").toString());
        Long adminId = SecurityUtil.getCurrentUserId();

        Long id = freeOrderActivityService.createActivity(name, startTime, grabTime, endTime, totalQuota, maxFreeAmount, adminId);
        log.info("[管理端] 创建免单活动 id={}", id);
        return R.ok(id);
    }

    @PostMapping("/{id}/publish")
    @Operation(summary = "发布免单活动")
    public R<Void> publishActivity(@PathVariable Long id) {
        freeOrderActivityService.publishActivity(id);
        log.info("[管理端] 发布免单活动 id={}", id);
        return R.ok();
    }

    @GetMapping
    @Operation(summary = "查询所有免单活动")
    public R<List<FreeOrderActivityVO>> listAll() {
        return R.ok(freeOrderActivityService.listAll());
    }
}

