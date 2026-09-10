package com.sakana;

import com.sakana.configs.SnowflakeAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * del-product 启动类
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableScheduling
@EnableAsync
@MapperScan({"com.sakana.dao.mapper", "com.sakana.review.dao.mapper"})
@Import(SnowflakeAutoConfiguration.class)
public class ProductApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductApplication.class, args);
    }
}
