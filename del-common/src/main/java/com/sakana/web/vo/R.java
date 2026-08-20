package com.sakana.web.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 统一返回结构
 * <p>
 * 所有 API 响应均使用此格式：
 * <pre>
 * {
 *   "code": 0,
 *   "message": "ok",
 *   "source": "del-product",
 *   "data": { ... },
 *   "timestamp": 1716000000000
 * }
 * </pre>
 *
 * @param <T> data 类型
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class R<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 业务状态码：0=成功，非0=失败
     * 成功时为 0，失败时为具体错误码（如 3001）
     */
    private Integer code;

    /**
     * 人类可读提示
     */
    private String message;

    /**
     * 响应来源服务名（微服务架构中用于标识是哪个服务返回的错误）
     * 正常情况由 GlobalExceptionHandler 自动填充，各服务无需手动设置
     */
    private String source;

    /**
     * 业务数据，可为对象/数组/null
     */
    private T data;

    /**
     * 响应时间戳（毫秒）
     */
    private long timestamp;

    public R() {
        this.timestamp = System.currentTimeMillis();
    }

    public R(Integer code, String message) {
        this.code = code;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
    }

    public R(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

    // ==================== 静态工厂方法 ====================

    public static <T> R<T> ok() {
        return new R<>(0, "ok");
    }

    public static <T> R<T> ok(T data) {
        return new R<>(0, "ok", data);
    }

    public static <T> R<T> ok(String message, T data) {
        return new R<>(0, message, data);
    }

    /**
     * 失败：指定错误码和消息
     */
    public static <T> R<T> fail(int code, String message) {
        return new R<>(code, message);
    }

    /**
     * 失败：仅指定消息，错误码默认为 9999
     */
    public static <T> R<T> fail(String message) {
        return new R<>(9999, message);
    }

    public static <T> R<T> of(int code, String message, T data) {
        return new R<>(code, message, data);
    }
}