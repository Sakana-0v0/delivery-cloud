package com.sakana;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * del-message 启动类
 *
 * <p>消息中心服务：
 * <ul>
 *   <li>C 端：站内消息查询、已读</li>
 *   <li>订阅 MQ 事件：订单创建 / 订单支付 / 商品评价等，落地站内消息 + 邮件</li>
 * </ul>
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@MapperScan("com.sakana.dao.mapper")
public class MessageApplication {

    public static void main(String[] args) {
        SpringApplication.run(MessageApplication.class, args);
    }
}
