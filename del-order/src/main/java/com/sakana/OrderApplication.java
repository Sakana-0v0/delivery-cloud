package com.sakana;

import com.sakana.feign.ProductFeignClient;
import com.sakana.feign.UserFeignClient;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * del-order 启动类
 *
 * <p>C 端订单服务，JWT 密钥与 del-user 共用（同一 C 端 token 池）。
 * <p>同时承担 admin 端订单管理接口（路径 /api/v1/admin/orders/**）。
 * <p>内置定时任务（{@code @EnableScheduling}）：超时关单 + 自动发货。
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(clients = {ProductFeignClient.class, UserFeignClient.class})
@EnableScheduling
@MapperScan("com.sakana.dao.mapper")
public class OrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }
}