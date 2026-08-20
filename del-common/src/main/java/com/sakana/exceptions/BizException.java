package com.sakana.exceptions;

import cn.hutool.core.util.StrUtil;
import lombok.Getter;

/**
 * 业务异常
 * <p>
 * 所有业务逻辑异常通过抛出此类表达，由 {@link GlobalExceptionHandler} 统一处理。
 * <p>
 * 支持三种构造方式：
 * <ul>
 *   <li>直接用枚举：new BizException(ProductErrorCode.NOT_FOUND)</li>
 *   <li>枚举 + 参数（填充占位符）：new BizException(ProductErrorCode.NOT_FOUND, 123)</li>
 *   <li>枚举 + 自定义消息（覆盖默认消息）：new BizException(ProductErrorCode.NOT_FOUND, "商品ID=123不存在")</li>
 * </ul>
 */
@Getter
public class BizException extends RuntimeException {

    /** 错误码（int） */
    private final int code;

    /** HTTP 状态码 */
    private final int httpStatus;

    /**
     * 构造一：直接使用枚举，默认消息
     */
    public BizException(BaseErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.httpStatus = errorCode.getHttpStatus();
    }

    /**
     * 构造二：枚举 + 可变参数（用于填充占位符）
     * 示例：new BizException(ProductErrorCode.NOT_FOUND, 123) → 消息变为 "商品[123]不存在"
     */
    public BizException(BaseErrorCode errorCode, Object... args) {
        super(StrUtil.format(errorCode.getMessage(), args));
        this.code = errorCode.getCode();
        this.httpStatus = errorCode.getHttpStatus();
    }

    /**
     * 构造三：枚举 + 自定义消息（完全覆盖枚举默认消息）
     * 示例：new BizException(ProductErrorCode.NOT_FOUND, "商品ID=123被删除了") → 消息为 "商品ID=123被删除了"
     */
    public BizException(BaseErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
        this.httpStatus = errorCode.getHttpStatus();
    }

    /**
     * 构造四：完全自定义（用于不希望定义枚举的场景，如中间件层）
     */
    public BizException(int code, String message, int httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }
}