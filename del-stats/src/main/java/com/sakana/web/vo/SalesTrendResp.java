package com.sakana.web.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 销售趋势响应
 */
@Data
public class SalesTrendResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 粒度：day / month */
    private String granularity;
    private List<SalesTrendVO> points;
}
