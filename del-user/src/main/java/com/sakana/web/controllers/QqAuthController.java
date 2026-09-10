package com.sakana.web.controllers;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakana.dao.entity.User;
import com.sakana.dao.mapper.UserMapper;
import com.sakana.utils.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * QQ 第三方登录 Controller（心月互联方案）
 *
 * 流程：
 *   1. 前端 → 心月互联 auth 接口（带 token） → QQ 官方 OAuth
 *   2. 用户授权 → 心月互联回调 /callback?code=xxx&msg=xxx
 *   3. 我们用 code 调心月互联 get_user_info 拿用户信息
 *   4. 查找或创建本地用户，生成 JWT
 *   5. 302 重定向到前端成功页（带 JWT）
 *
 * <p>#BUG-004 修复：使用显式构造器并加 @Qualifier("externalRestTemplate")，
 * 因为 Lombok 的 @RequiredArgsConstructor 不会自动传递字段上的 @Qualifier 注解到构造函数参数。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth/qq")
public class QqAuthController {

    @Value("${qq.base-url:https://qq.wch666.com}")
    private String baseUrl;

    @Value("${qq.frontend-base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String ROLE = "USER";

    /**
     * 显式构造器（不使用 Lombok 的 @RequiredArgsConstructor）
     * <p>
     * 关键：通过 @Qualifier("externalRestTemplate") 明确指定使用外部 API 调用的 RestTemplate，
     * 避免使用默认的 @LoadBalanced RestTemplate（不能解析 qq.wch666.com 等外部域名）。
     */
    public QqAuthController(
            UserMapper userMapper,
            JwtUtil jwtUtil,
            @Qualifier("externalRestTemplate") RestTemplate restTemplate) {
        this.userMapper = userMapper;
        this.jwtUtil = jwtUtil;
        this.restTemplate = restTemplate;
    }

    /**
     * QQ 登录回调（心月互联 302 重定向到此接口）
     *
     * 心月互联传递参数：
     *   - code: 授权码，用于获取用户信息
     *   - msg: 状态标识（同发起时携带的 msg）
     */
    @GetMapping("/callback")
    public void callback(
            @RequestParam String code,
            @RequestParam(required = false) String msg,
            HttpServletResponse response) throws IOException {

        log.info("[QQ登录] 收到回调 code={}, msg={}", code, msg);

        // ========== 第一步：用 code 调心月互联拿用户信息 ==========
        String userInfoUrl = baseUrl + "/api/get_user_info.php?code=" + code;
        String userInfoRaw;
        try {
            userInfoRaw = restTemplate.getForObject(userInfoUrl, String.class);
        } catch (Exception e) {
            log.error("[QQ登录] 调用心月互联失败: {}", e.getMessage());
            response.sendRedirect(buildErrorRedirect("QQ登录服务暂不可用，请稍后重试"));
            return;
        }

        log.info("[QQ登录] 心月互联响应: {}", userInfoRaw);

        // ========== 第二步：解析用户信息 ==========
        if (userInfoRaw == null || userInfoRaw.trim().isEmpty() || "error".equals(userInfoRaw.trim())) {
            log.warn("[QQ登录] 心月互联返回错误: {}", userInfoRaw);
            response.sendRedirect(buildErrorRedirect("QQ授权失败，未获取到用户信息"));
            return;
        }

        String openId, nickname, avatar;
        try {
            JsonNode node = objectMapper.readTree(userInfoRaw);
            int ret = node.has("ret") ? node.get("ret").asInt() : -1;
            if (ret != 0) {
                String errMsg = node.has("msg") ? node.get("msg").asText() : "QQ授权失败";
                log.warn("[QQ登录] 心月互联返回错误码 ret={}, msg={}", ret, errMsg);
                response.sendRedirect(buildErrorRedirect(errMsg));
                return;
            }
            openId = node.get("open_id").asText();
            nickname = node.has("nickname") ? node.get("nickname").asText() : "QQ用户";
            avatar = node.has("figureurl_2") ? node.get("figureurl_2").asText() : null;
        } catch (Exception e) {
            log.error("[QQ登录] 解析用户信息失败: {}", e.getMessage());
            response.sendRedirect(buildErrorRedirect("用户信息解析失败"));
            return;
        }

        // ========== 第三步：查找或创建本地用户 ==========
        User user = findOrCreateUser(openId, nickname, avatar);

        // ========== 第四步：生成 JWT ==========
        String token = jwtUtil.generateAccessToken(user.getId(), user.getUsername(), ROLE);
        log.info("[QQ登录] 成功 username={}, userId={}", user.getUsername(), user.getId());

        // ========== 第五步：302 重定向到前端成功页 ==========
        String redirectUrl = frontendBaseUrl + "/login/qq-success"
                + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8)
                + "&nickname=" + URLEncoder.encode(nickname, StandardCharsets.UTF_8);
        response.sendRedirect(redirectUrl);
    }

    /**
     * 根据 open_id 查找用户，未找到则创建
     */
    private User findOrCreateUser(String openId, String nickname, String avatar) {
        // 先按 open_id 查
        User existing = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getOpenId, openId)
                        .eq(User::getIsDeleted, 0)
        );

        if (existing != null) {
            // 更新昵称和头像（可能变了）
            existing.setNickname(nickname);
            if (avatar != null) {
                existing.setQqAvatar(avatar);
            }
            existing.setLastLoginTime(LocalDateTime.now());
            userMapper.updateById(existing);
            log.info("[QQ登录] 老用户登录 username={}", existing.getUsername());
            return existing;
        }

        // 创建新用户：username = "qq_" + 8位UUID
        User newUser = new User();
        newUser.setUsername("qq_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        newUser.setNickname(nickname);
        newUser.setQqNickname(nickname);
        newUser.setOpenId(openId);
        newUser.setQqAvatar(avatar);
        newUser.setStatus(0);
        newUser.setLastLoginTime(LocalDateTime.now());
        newUser.setCreateTime(LocalDateTime.now());
        newUser.setUpdateTime(LocalDateTime.now());
        // password 留 null（QQ 用户免密登录）
        userMapper.insert(newUser);
        log.info("[QQ登录] 新用户注册 username={}", newUser.getUsername());
        return newUser;
    }

    /**
     * 构建错误重定向 URL
     */
    private String buildErrorRedirect(String msg) {
        return frontendBaseUrl + "/login/qq-success?error="
                + URLEncoder.encode(msg, StandardCharsets.UTF_8);
    }
}
