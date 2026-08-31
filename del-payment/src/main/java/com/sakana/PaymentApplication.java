package com.sakana;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * del-payment 启动类。
 *
 * <p>支付微服务：聚合支付、回调处理、发布 payment.order.paid 事件。
 * <p>通过 Feign 与 del-order / del-user 通信，通过 RabbitMQ 发布事件。
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.sakana.feign")
@EnableScheduling
@EnableAsync
@MapperScan("com.sakana.dao.mapper")
public class PaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentApplication.class, args);
    }
}
