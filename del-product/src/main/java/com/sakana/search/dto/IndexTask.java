package com.sakana.search.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 索引任务状态 DTO（#SEARCH-001）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndexTask {

    /** 任务开始时间 */
    private LocalDateTime startTime;

    /** 任务结束时间 */
    private LocalDateTime endTime;

    /** 待索引总商品数 */
    private Integer total;

    /** 成功数 */
    private Integer success;

    /** 失败数 */
    private Integer failed;

    /** 任务状态：RUNNING / SUCCESS / FAILED */
    private String status;

    /** 错误信息 */
    private String errorMsg;
}
