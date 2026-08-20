package com.sakana.configs;

import com.sakana.utils.JwtUtil;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JWT Bean 配置
 *
 * <p>JwtProperties 和 JwtUtil 在 del-common 中定义，
 * 每个服务使用各自的密钥独立签发/验证 token。
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtAutoConfiguration {

    @Bean
    public JwtUtil jwtUtil(JwtProperties jwtProperties) {
        return new JwtUtil(jwtProperties);
    }
}
