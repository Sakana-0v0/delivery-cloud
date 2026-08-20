package com.sakana.web.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 分类视图对象
 */
@Data
public class CategoryVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private Integer sort;
    private Integer status;
}