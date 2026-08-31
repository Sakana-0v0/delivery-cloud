package com.sakana.exceptions;

import com.sakana.web.vo.R;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Objects;

/**
 * 统一全局异常处理器
 *
 * <p>仅在 Servlet Web 应用中加载。
 */
@Slf4j
@RestControllerAdvice
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class GlobalExceptionHandler {

    @Autowired
    private Environment environment;

    // ==================== 业务异常 ====================

    @ExceptionHandler(BizException.class)
    @ResponseStatus(HttpStatus.OK)
    public R<Void> handleBizException(BizException e, HttpServletRequest request) {
        log.warn("[业务异常] code={}, httpStatus={}, message={}", e.getCode(), e.getHttpStatus(), e.getMessage());
        return withSource(e.getMessage(), e.getCode(), request);
    }

    // ==================== 参数校验异常 ====================

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleValidationException(MethodArgumentNotValidException e, HttpServletRequest request) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .findFirst().orElse("参数校验失败");
        log.warn("[参数校验失败] {}", message);
        return withSource(message, 1001, request);
    }

    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleBindException(BindException e, HttpServletRequest request) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .findFirst().orElse("参数绑定失败");
        log.warn("[参数绑定失败] {}", message);
        return withSource(message, 1001, request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleMissingParam(MissingServletRequestParameterException e, HttpServletRequest request) {
        String message = "缺少必填参数: " + e.getParameterName();
        log.warn("[缺少必填参数] {}", message);
        return withSource(message, 1002, request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        String message = "参数类型不匹配: " + Objects.requireNonNull(e.getName());
        log.warn("[参数类型不匹配] {}", message);
        return withSource(message, 1001, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleConstraintViolation(ConstraintViolationException e, HttpServletRequest request) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage).findFirst().orElse("参数校验失败");
        log.warn("[约束校验失败] {}", message);
        return withSource(message, 1001, request);
    }

    // ==================== 路由/请求方法异常 ====================

    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public R<Void> handleNoHandlerFound(NoHandlerFoundException e, HttpServletRequest request) {
        log.warn("[资源不存在] {}", e.getMessage());
        return withSource("资源不存在", 404, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public R<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        log.warn("[请求方法不支持] {}", e.getMessage());
        return withSource("请求方法不允许", 1002, request);
    }

    // ==================== 安全异常 ====================

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public R<Void> handleAccessDenied(AccessDeniedException e, HttpServletRequest request) {
        log.warn("[无权限访问] {}", e.getMessage());
        return withSource("无权限访问", 403, request);
    }

    @ExceptionHandler({AuthenticationException.class, BadCredentialsException.class})
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public R<Void> handleAuthenticationException(Exception e, HttpServletRequest request) {
        log.warn("[认证失败] {}", e.getMessage());
        return withSource("认证失败", 401, request);
    }

    // ==================== 系统兜底异常（含堆栈，方便排查）====================

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("[系统异常] 类型={}, 消息={}", e.getClass().getName(), e.getMessage(), e);
        String message = "系统异常: " + e.getClass().getSimpleName() + ": " + e.getMessage();
        // 包含堆栈的前 8 行（用于诊断）
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        String[] lines = sw.toString().split("\\r?\\n");
        StringBuilder sb = new StringBuilder(message);
        for (int i = 0; i < Math.min(8, lines.length); i++) {
            sb.append(" | ").append(lines[i].trim());
        }
        return withSource(sb.toString(), 9999, request);
    }

    // ==================== 私有 ====================

    private R<Void> withSource(String message, int code, HttpServletRequest request) {
        R<Void> r = R.fail(code, message);
        r.setSource(determineSource(request));
        return r;
    }

    private String determineSource(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri != null && uri.startsWith("/")) {
            String[] segments = uri.split("/");
            if (segments.length >= 2) {
                return segments[1];
            }
        }
        return environment.getProperty("spring.application.name", "unknown");
    }
}
