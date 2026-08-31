package com.sakana.admin.web.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 登录响应
 */
@Data
@Schema(description = "登录响应")
public class AdminLoginResp {

    @Schema(description = "访问令牌")
    private String accessToken;

    @Schema(description = "刷新令牌")
    private String refreshToken;

    @Schema(description = "过期时间（秒）")
    private Long expiresIn;

    @Schema(description = "用户信息")
    private UserInfo userInfo;

    @Data
    public static class UserInfo {
        private Long id;
        private String username;
        private String nickname;
        private String role;
    }
}
