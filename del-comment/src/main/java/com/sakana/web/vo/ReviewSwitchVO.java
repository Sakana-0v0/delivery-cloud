package com.sakana.web.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 评价开关视图对象
 * <p>
 * 开关语义：是否允许用户提交/修改评价（操作锁）。
 * 开关关闭时 👍/👎 组件仍正常渲染，仅禁止改变评价状态。
 */
@Data
public class ReviewSwitchVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 👍 评价开关：true=开 */
    private boolean praiseOpen;

    /** 👎 评价开关：true=开 */
    private boolean badOpen;
}
