package com.sakana.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 分类创建/修改请求
 */
@Data
@Schema(description = "分类创建/修改请求")
public class CategoryReq {

    @NotBlank(message = "分类名称不能为空")
    @Size(max = 50, message = "分类名称长度不能超过 50")
    @Schema(description = "分类名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(description = "排序（升序）", defaultValue = "0")
    private Integer sort = 0;

    @Schema(description = "状态：0启用 1禁用", defaultValue = "0", allowableValues = {"0", "1"})
    private Integer status = 0;
}