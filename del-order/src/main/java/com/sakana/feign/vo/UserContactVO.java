package com.sakana.feign.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户联系信息（简化版，用于内部服务调用）
 */
@Data
public class UserContactVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String username;
    private String email;
}

