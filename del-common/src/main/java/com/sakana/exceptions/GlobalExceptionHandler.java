package com.sakana.exceptions;

import com.sakana.web.vo.R;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.Objects;

/**
 * 统一全局异常处理器
 * <p>
 * 放在 del-common 中，所有依赖该模块的微服务均可自动复用。
 * 如需覆盖特定异常的处理逻辑，可在具体服务中定义子类并使用 @Order 优先级更高。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {


    @Autowired
    private Environment environment;

    // ==================== 业务异常 ====================

    @ExceptionHandler(BizException.class)
    @ResponseStatus(HttpStatus.OK)  // 业务异常按错误码中的 httpStatus 返回，但 body 中仍返回业务错误码
    public R<Void> handleBizException(BizException e, HttpServletRequest request) {
        log.warn("[业务异常] code={}, httpStatus={}, message={}", e.getCode(), e.getHttpStatus(), e.getMessage());
        R<Void> r = R.fail(e.getCode(), e.getMessage());
        r.setSource(determineSource(request));
        return r;
    }

    // ==================== 参数校验异常 ====================

    /**
     * @RequestBody 参数校验失败（@Valid 注解）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleValidationException(MethodArgumentNotValidException e, HttpServletRequest request) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .findFirst()
                .orElse("参数校验失败");
        log.warn("[参数校验失败] {}", message);
        R<Void> r = R.fail(1001, message);  // 1001 = PARAM_INVALID
        r.setSource(determineSource(request));
        return r;
    }

    /**
     * 表单参数绑定失败
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleBindException(BindException e, HttpServletRequest request) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .findFirst()
                .orElse("参数绑定失败");
        log.warn("[参数绑定失败] {}", message);
        R<Void> r = R.fail(1001, message);
        r.setSource(determineSource(request));
        return r;
    }

    /**
     * @RequestParam 必填参数缺失
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleMissingParam(MissingServletRequestParameterException e, HttpServletRequest request) {
        String message = "缺少必填参数: " + e.getParameterName();
        log.warn("[缺少必填参数] {}", message);
        R<Void> r = R.fail(1002, message);  // 1002 = PARAM_MISSING
        r.setSource(determineSource(request));
        return r;
    }

    /**
     * 路径参数类型不匹配（如 Long id 传了 abc）
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        String message = "参数类型不匹配: " + Objects.requireNonNull(e.getName());
        log.warn("[参数类型不匹配] {}", message);
        R<Void> r = R.fail(1001, message);
        r.setSource(determineSource(request));
        return r;
    }

    /**
     * @Validated 路径变量/请求参数约束校验（如 @Min、@Max）
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleConstraintViolation(ConstraintViolationException e, HttpServletRequest request) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .findFirst()
                .orElse("参数校验失败");
        log.warn("[约束校验失败] {}", message);
        R<Void> r = R.fail(1001, message);
        r.setSource(determineSource(request));
        return r;
    }

    // ==================== 路由/请求方法异常 ====================

    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public R<Void> handleNoHandlerFound(NoHandlerFoundException e, HttpServletRequest request) {
        log.warn("[资源不存在] {}", e.getMessage());
        R<Void> r = R.fail(404, "资源不存在");
        r.setSource(determineSource(request));
        return r;
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public R<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        log.warn("[请求方法不支持] {}", e.getMessage());
        R<Void> r = R.fail(1002, "请求方法不允许");
        r.setSource(determineSource(request));
        return r;
    }

    // ==================== 安全异常 ====================

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public R<Void> handleAccessDenied(AccessDeniedException e, HttpServletRequest request) {
        log.warn("[无权限访问] {}", e.getMessage());
        R<Void> r = R.fail(403, "无权限访问");
        r.setSource(determineSource(request));
        return r;
    }

    @ExceptionHandler({AuthenticationException.class, BadCredentialsException.class})
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public R<Void> handleAuthenticationException(Exception e, HttpServletRequest request) {
        log.warn("[认证失败] {}", e.getMessage());
        R<Void> r = R.fail(401, "认证失败");
        r.setSource(determineSource(request));
        return r;
    }

    // ==================== 系统兜底异常 ====================

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("[系统异常] 类型={}, 消息={}", e.getClass().getName(), e.getMessage(), e);
        R<Void> r = R.fail(9999, "系统异常");
        r.setSource(determineSource(request));
        return r;
    }


    private String determineSource(HttpServletRequest request) {
        // 优先从请求路径提取，如 /del-product/api/... → del-product
        String uri = request.getRequestURI();
        if (uri != null && uri.startsWith("/")) {
            String[] segments = uri.split("/");
            // /api/v1/products → segments[1]="api", segments[2]="v1", segments[3]="products"
            // 通常第二个路径段是服务名: /del-product/xxx → del-product
            if (segments.length >= 2) {
                return segments[1];
            }
        }
        // 兜底：从 spring.application.name 获取
        return environment.getProperty("spring.application.name", "unknown");
    }
}