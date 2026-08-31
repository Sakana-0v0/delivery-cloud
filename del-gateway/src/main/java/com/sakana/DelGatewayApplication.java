package com.sakana;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * del-gateway 网关服务
 *
 * <p>职责：
 * <ul>
 *   <li>路由转发（按路径前缀路由到对应微服务）</li>
 *   <li>JWT 双密钥鉴权（C 端 + B 端 token 池）</li>
 *   <li>基于角色的访问控制（/api/v1/admin/** vs /api/v1/user/**）</li>
 *   <li>内部服务调用鉴权（X-Internal-Service-Token）</li>
 *   <li>用户信息透传（X-User-* headers）</li>
 * </ul>
 *
 * <p>服务层 JwtAuthenticationFilter 仍保留作为纵深防御
 */
@SpringBootApplication
@EnableDiscoveryClient
public class DelGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(DelGatewayApplication.class, args);
    }
}
