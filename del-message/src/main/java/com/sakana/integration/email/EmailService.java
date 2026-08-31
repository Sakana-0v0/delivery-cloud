package com.sakana.integration.email;

/**
 * 邮件发送抽象（站内信与邮件同属消息中心，未来可平移到独立服务）
 */
public interface EmailService {

    /**
     * 发送纯文本邮件
     */
    void send(String to, String subject, String content);

    /**
     * 发送 HTML 邮件
     */
    void sendHtml(String to, String subject, String html);
}
