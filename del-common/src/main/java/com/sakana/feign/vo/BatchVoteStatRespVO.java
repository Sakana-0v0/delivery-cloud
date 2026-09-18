package com.sakana.feign.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 批量评价统计响应（与 del-product 的 BatchVoteStatRespVO 字段对齐）
 */
@Data
public class BatchVoteStatRespVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<ReviewVoteStatItem> results;
}
