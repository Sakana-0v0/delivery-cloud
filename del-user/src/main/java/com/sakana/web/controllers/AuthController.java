package com.sakana.web.controllers;

import com.sakana.exceptions.BizException;
import com.sakana.request.LoginReq;
import com.sakana.request.RegisterReq;
import com.sakana.request.SendCodeReq;
import com.sakana.security.LoginUser;
import com.sakana.security.TokenBlacklist;
import com.sakana.services.UserService;
import com.sakana.services.VerifyCodeService;
import com.sakana.utils.JwtUtil;
import com.sakana.web.vo.LoginResp;
import com.sakana.web.vo.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 认证接口（注册/登录/登出/刷新Token）
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "认证", description = "用户注册、登录、登出、刷新Token")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final TokenBlacklist tokenBlacklist;
    private final UserService userService;
    private final VerifyCodeService verifyCodeService;

    @PostMapping("/register")
    @Operation(summary = "用户注册")
    public R<Void> register(@Valid @RequestBody RegisterReq req) {
        com.sakana.dao.entity.User user = userService.register(req);
        log.info("[注册成功] username={}", user.getUsername());
        return R.ok();
    }

    @PostMapping("/send-code")
    @Operation(summary = "发送邮箱验证码")
    public R<Void> sendCode(@Valid @RequestBody SendCodeReq req) {
        verifyCodeService.sendCode(req.getEmail(), "register");
        return R.ok();
    }

    @PostMapping("/login")
    @Operation(summary = "用户登录")
    public R<LoginResp> login(@Valid @RequestBody LoginReq req) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.getUsername(), req.getPassword())
            );
            LoginUser loginUser = (LoginUser) authentication.getPrincipal();

            String accessToken = jwtUtil.generateAccessToken(
                    loginUser.getUserId(), loginUser.getUsername(), loginUser.getRole());
            String refreshToken = jwtUtil.generateRefreshToken(
                    loginUser.getUserId(), loginUser.getUsername(), loginUser.getRole());

            LoginResp resp = new LoginResp();
            resp.setAccessToken(accessToken);
            resp.setRefreshToken(refreshToken);
            resp.setExpiresIn(jwtUtil.getExpirationSeconds(accessToken));

            LoginResp.UserInfo userInfo = new LoginResp.UserInfo();
            userInfo.setId(loginUser.getUserId());
            userInfo.setUsername(loginUser.getUsername());
            userInfo.setNickname(loginUser.getNickname());
            userInfo.setRole(loginUser.getRole());
            resp.setUserInfo(userInfo);

            log.info("[登录成功] username={}", req.getUsername());
            // 登记 jti 到用户索引（用于后续冻结踢下线）
            tokenBlacklist.registerUserJti(loginUser.getUserId(), jwtUtil.getJti(accessToken));
            return R.ok(resp);
        } catch (BadCredentialsException e) {
            log.warn("[登录失败] 用户名或密码错误: {}", req.getUsername());
            throw new BizException(com.sakana.services.impl.UserErrorCode.LOGIN_FAIL);
        }
    }

    @PostMapping("/refresh")
    @Operation(summary = "刷新Token")
    public R<LoginResp> refresh(@RequestBody Map<String, String> req) {
        String refreshToken = req.get("refreshToken");
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BizException(com.sakana.services.impl.UserErrorCode.PARAM_MISSING);
        }
        if (!jwtUtil.validateToken(refreshToken)) {
            throw new BizException(com.sakana.services.impl.UserErrorCode.REFRESH_TOKEN_INVALID);
        }

        Long userId = jwtUtil.getUserId(refreshToken);
        String username = jwtUtil.getUsername(refreshToken);
        String role = jwtUtil.getRole(refreshToken);

        String newAccessToken = jwtUtil.generateAccessToken(userId, username, role);
        String newRefreshToken = jwtUtil.generateRefreshToken(userId, username, role);

        LoginResp resp = new LoginResp();
        resp.setAccessToken(newAccessToken);
        resp.setRefreshToken(newRefreshToken);
        resp.setExpiresIn(jwtUtil.getExpirationSeconds(newAccessToken));

        LoginResp.UserInfo userInfo = new LoginResp.UserInfo();
        userInfo.setId(userId);
        userInfo.setUsername(username);
        userInfo.setRole(role);
        resp.setUserInfo(userInfo);

        return R.ok(resp);
    }

    @PostMapping("/logout")
    @Operation(summary = "登出")
    public R<Void> logout(@RequestHeader("Authorization") String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                String jti = jwtUtil.getJti(token);
                long ttl = jwtUtil.getExpirationSeconds(token);
                Long userId = jwtUtil.getUserId(token);
                if (ttl > 0) {
                    tokenBlacklist.add(jti, ttl);
                }
                tokenBlacklist.unregisterUserJti(userId, jti);
                log.info("[登出] token已加入黑名单: jti={}, userId={}", jti, userId);
            } catch (Exception e) {
                log.warn("[登出] token解析失败", e);
            }
        }
        return R.ok();
    }
}
