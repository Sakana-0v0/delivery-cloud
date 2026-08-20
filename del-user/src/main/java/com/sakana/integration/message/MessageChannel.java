package com.sakana.integration.message;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 消息通道桩（Stub）
 *
 * <p>当前实现仅打印日志，不真正发送消息。
 * 后续集成真实消息服务（如 MQ、邮件网关）时替换此实现即可。
 */
@Slf4j
@Component
public class MessageChannel {

    /**
     * 发送消息
     *
     * @param type    消息类型：email / sms 等
     * @param target  目标地址：邮箱 / 手机号
     * @param subject 主题
     * @param template 模板名
     * @param params  模板参数
     */
    public void send(String type, String target, String subject,
                     String template, Map<String, Object> params) {
        log.info("[MessageChannel][STUB] 消息发送（未真正发送）| type={}, target={}, subject={}, template={}, params={}",
                type, target, subject, template, params);
    }
}
