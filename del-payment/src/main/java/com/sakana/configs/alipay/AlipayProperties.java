package com.sakana.configs.alipay;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 支付宝配置属性
 */
@Data
@ConfigurationProperties(prefix = "alipay")
public class AlipayProperties {

    /**
     * 支付宝应用ID
     */
    private String appId;

    /**
     * 应用私钥
     */
    private String privateKey;

    /**
     * 支付宝公钥
     */
    private String publicKey;

    /**
     * 支付宝网关
     */
    private String gateway;

    /**
     * 异步通知地址
     */
    private String notifyUrl;

    /**
     * 同步跳转地址
     */
    private String returnUrl;
}