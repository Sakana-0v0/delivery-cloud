package com.sakana.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求
 */
@Data
@Schema(description = "注册请求")
public class RegisterReq {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 20, message = "用户名3-20位")
    @Schema(description = "用户名")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码6-20位")
    @Schema(description = "密码")
    private String password;

    @Schema(description = "手机号")
    private String phone;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Schema(description = "邮箱（必填）")
    private String email;

    @NotBlank(message = "验证码不能为空")
    @Schema(description = "邮箱验证码")
    private String code;
}
