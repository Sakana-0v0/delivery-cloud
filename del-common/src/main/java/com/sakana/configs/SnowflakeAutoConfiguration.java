package com.sakana.configs;

import com.sakana.utils.SnowflakeIdGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Snowflake 自动配置
 *
 * <p>从 del-IdGenerator 服务下沉而来，每个服务本地生成 ID，无 RPC 开销。
 *
 * <p>使用方式：在应用的 @SpringBootApplication 上加 @Import(SnowflakeAutoConfiguration.class)
 */
@Configuration
@EnableConfigurationProperties(SnowflakeProperties.class)
public class SnowflakeAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SnowflakeIdGenerator snowflakeIdGenerator(SnowflakeProperties properties) {
        long workerId = properties.getWorkerId() != null ?
                properties.getWorkerId() :
                generateWorkerIdByIp();
        return new SnowflakeIdGenerator(workerId, properties.getDatacenterId(), properties.getEpoch());
    }

    /**
     * 基于 IP 地址自动生成 Worker ID
     */
    private long generateWorkerIdByIp() {
        try {
            InetAddress address = InetAddress.getLocalHost();
            String hostAddress = address.getHostAddress();
            String[] parts = hostAddress.split("\\.");
            if (parts.length == 4) {
                return Long.parseLong(parts[3]) % 1024;
            }
            return Math.abs(hostAddress.hashCode()) % 1024;
        } catch (UnknownHostException e) {
            return 0L;
        }
    }
}
