package com.sakana.web.vo;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 订单分页响应
 */
public class OrderPageResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long total;
    private Long page;
    private Long size;
    private List<OrderVO> records = new ArrayList<>();

    public Long getTotal() { return total; }
    public void setTotal(Long total) { this.total = total; }

    public Long getPage() { return page; }
    public void setPage(Long page) { this.page = page; }

    public Long getSize() { return size; }
    public void setSize(Long size) { this.size = size; }

    public List<OrderVO> getRecords() { return records; }
    public void setRecords(List<OrderVO> records) { this.records = records; }
}
