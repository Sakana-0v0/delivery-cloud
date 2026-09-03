package com.sakana.configs;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigInteger;

/**
 * Jackson 全局配置
 *
 * <p>核心目的：
 * <ol>
 *   <li>解决雪花 ID 在前端 JavaScript 中丢失精度的问题（Long -> String）</li>
 *   <li>禁用"未知属性失败"特性，让 Feign 调用更健壮</li>
 * </ol>
 *
 * <h2>使用方式</h2>
 * 通过 {@link Jackson2ObjectMapperBuilderCustomizer} 而不是直接覆盖 ObjectMapper Bean，
 * 这样可以保留 Spring Boot 自动配置的 ObjectMapper 的所有默认行为（包含 Nacos 中的配置）。
 *
 * <h2>为什么不用 @Primary ObjectMapper</h2>
 * 直接覆盖 ObjectMapper Bean 会丢失 Nacos common-jackson.yml 中的所有配置（包括
 * fail-on-unknown-properties、date-format 等），导致跨服务调用时遇到新字段就报错。
 *
 * @author sakana
 * @since 2026-09-03
 */
@Configuration
public class JacksonConfig {

    /**
     * 自定义 Jackson 配置（推荐方式）
     *
     * <p>通过 {@link Jackson2ObjectMapperBuilderCustomizer} 修改 Spring Boot 自动配置的 ObjectMapper。
     * 这种方式不会覆盖整个 Bean，而是与 Nacos 配置合并：
     * <ul>
     *   <li>Nacos 中的 fail-on-unknown-properties: false 仍然生效</li>
     *   <li>Spring Boot 默认的 LocalDateTime 等模块注册仍然生效</li>
     *   <li>只是新增了 Long -> String 序列化器</li>
     * </ul>
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer longToStringCustomizer() {
        return builder -> {
            // 注册 JSR-310 时间模块
            builder.modulesToInstall(new JavaTimeModule());

            // 创建 SimpleModule，注册 Long 转 String 的序列化器
            SimpleModule module = new SimpleModule("LongToStringModule");
            module.addSerializer(Long.class, ToStringSerializer.instance);
            module.addSerializer(Long.TYPE, ToStringSerializer.instance);
            module.addSerializer(long.class, ToStringSerializer.instance);
            module.addSerializer(BigInteger.class, ToStringSerializer.instance);

            builder.modulesToInstall(module);
        };
    }
}
