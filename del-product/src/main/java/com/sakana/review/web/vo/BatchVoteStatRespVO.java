package com.sakana.review.web.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 批量投票统计出参包装
 */
@Data
public class BatchVoteStatRespVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<BatchVoteStatItemVO> results;
}
