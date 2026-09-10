package com.sakana.cs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 智能客服服务（#AI-CS-001-MVP）
 *
 * <p>只扫描 com.sakana.cs 包，避免引入 del-product/del-order 的组件
 * <p>端口: 10011（由 Nacos del-cs.yml 指定）
 */
@SpringBootApplication(scanBasePackages = "com.sakana.cs")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.sakana.cs.feign")
public class CsApplication {

    public static void main(String[] args) {
        SpringApplication.run(CsApplication.class, args);
    }
}