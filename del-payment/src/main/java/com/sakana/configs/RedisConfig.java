package com.sakana.configs;

import com.sakana.feign.vo.OrderSnapshotVO;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置：注册专用的 RedisTemplate Bean。
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, OrderSnapshotVO> orderSnapshotRedisTemplate(
            RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, OrderSnapshotVO> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new Jackson2JsonRedisSerializer<>(OrderSnapshotVO.class));
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new Jackson2JsonRedisSerializer<>(OrderSnapshotVO.class));
        template.afterPropertiesSet();
        return template;
    }
}