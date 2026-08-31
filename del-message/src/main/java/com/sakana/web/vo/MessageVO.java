package com.sakana.web.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 消息 VO
 */
@Data
public class MessageVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private String type;
    private String title;
    private String content;
    private Integer isRead;
    private String bizId;
    private LocalDateTime createTime;
}
