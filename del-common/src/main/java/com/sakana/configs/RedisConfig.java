package com.sakana.configs;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.protocol.ProtocolVersion;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Unified Redis client configuration.
 *
 * <p>Forces Lettuce to use RESP2 protocol to avoid the
 * NOAUTH / HELLO command-ordering issue seen with Lettuce 6.x
 * and password-protected Redis instances.
 */
@Configuration
public class RedisConfig {

    @Bean
    public LettuceClientConfigurationBuilderCustomizer lettuceClientCustomizer() {
        return builder -> builder.clientOptions(
                ClientOptions.builder()
                        .protocolVersion(ProtocolVersion.RESP2)
                        .build()
        );
    }
}
