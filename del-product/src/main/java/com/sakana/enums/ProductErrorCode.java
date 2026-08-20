package com.sakana.enums;

import com.sakana.exceptions.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 商品服务错误码（3xxx 号段）
 */
@Getter
@AllArgsConstructor
public enum ProductErrorCode implements BaseErrorCode {

    NOT_FOUND(3001, "商品[%s]不存在", 404),
    PRODUCT_OFF_SHELF(3002, "商品已下架", 400),
    STATUS_INVALID(3003, "商品状态[%s]无法变更为[%s]", 400),
    STOCK_LOCK_FAIL(3004, "库存锁定失败，商品ID[%s]", 503),
    STOCK_NOT_ENOUGH(3005, "库存不足", 400),
    STOCK_INVALID(3006, "库存值非法", 400),
    CATEGORY_HAS_PRODUCT(3007, "该分类下还有商品", 400),
    CATEGORY_NOT_FOUND(3008, "分类不存在", 404),
    ;

    private final int code;
    private final String message;
    private final int httpStatus;
}
