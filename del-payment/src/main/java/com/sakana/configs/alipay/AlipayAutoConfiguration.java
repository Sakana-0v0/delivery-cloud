package com.sakana.configs.alipay;

import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 支付宝自动配置
 * <p>
 * 当 classpath 中存在 alipay-sdk-java 时，自动注册 AlipayClient Bean。
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(AlipayProperties.class)
@ConditionalOnClass(name = "com.alipay.api.AlipayClient")
public class AlipayAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AlipayClient alipayClient(AlipayProperties properties) {
        log.info("========== 支付宝配置加载 ==========");
        log.info("[AlipayConfig] appId     = {}", properties.getAppId());
        log.info("[AlipayConfig] gateway    = {}", properties.getGateway());
        log.info("[AlipayConfig] privateKey= {}... (长度={})",
                properties.getPrivateKey() != null ? properties.getPrivateKey().substring(0, Math.min(20, properties.getPrivateKey().length())) : "NULL",
                properties.getPrivateKey() != null ? properties.getPrivateKey().length() : 0);
        log.info("[AlipayConfig] publicKey = {}... (长度={})",
                properties.getPublicKey() != null ? properties.getPublicKey().substring(0, Math.min(20, properties.getPublicKey().length())) : "NULL",
                properties.getPublicKey() != null ? properties.getPublicKey().length() : 0);
        log.info("[AlipayConfig] notifyUrl = {}", properties.getNotifyUrl());
        log.info("[AlipayConfig] returnUrl = {}", properties.getReturnUrl());
        log.info("====================================");

        log.info("[AlipayConfig] 开始创建 AlipayClient，gateway={}", properties.getGateway());

        AlipayClient client = new DefaultAlipayClient(
                properties.getGateway(),
                properties.getAppId(),
                properties.getPrivateKey(),
                "json",
                "UTF-8",
                properties.getPublicKey(),
                "RSA2"
        );

        log.info("[AlipayConfig] AlipayClient 创建完成");
        return client;
    }
}