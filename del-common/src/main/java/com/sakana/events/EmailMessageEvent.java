package com.sakana.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * 邮件消息事件（del-user → del-message）
 * <p>
 * 通过 user.message.exchange (topic) 投递，routing key = "email.send"。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmailMessageEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 目标邮箱 */
    private String to;

    /** 主题 */
    private String subject;

    /** 模板名 */
    private String template;

    /** 模板参数 */
    private Map<String, Object> params;
}
