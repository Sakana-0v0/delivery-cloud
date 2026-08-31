package com.sakana.review.dto.request.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理后台 - 评价开关切换请求
 */
@Data
@Schema(description = "评价开关切换请求")
public class ReviewSwitchReq {

    @NotNull(message = "open 不能为空")
    @Schema(description = "1=开 0=关", requiredMode = Schema.RequiredMode.REQUIRED,
            allowableValues = {"0", "1"})
    private Integer open;
}
