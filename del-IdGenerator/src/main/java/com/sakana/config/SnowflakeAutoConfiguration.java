package com.sakana.config;

import com.sakana.service.SnowflakeIdGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.net.InetAddress;
import java.net.UnknownHostException;

@Configuration
@EnableConfigurationProperties(SnowflakeProperties.class)
public class SnowflakeAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SnowflakeIdGenerator snowflakeIdGenerator(SnowflakeProperties properties) {
        //根据当前机器IP动态生成workerId
        long workerId = properties.getWorkerId() != null ?
                properties.getWorkerId() :
                generateWorkerIdByIp();
        return new SnowflakeIdGenerator(workerId, properties.getDatacenterId(), properties.getEpoch());
    }

    /**
     * 基于IP地址自动生成 Worker ID（经典策略）
     * 取 IPv4 地址最后一段，或对整个 IP 取哈希后对 1024 取模
     */
    private long generateWorkerIdByIp() {
        try {
            InetAddress address = InetAddress.getLocalHost();
            String hostAddress = address.getHostAddress();
            // 简单策略：取最后一段IP（若为IPv4），如 192.168.1.100 -> 100
            String[] parts = hostAddress.split("\\.");
            if (parts.length == 4) {
                return Long.parseLong(parts[3]) % 1024;
            }
            // 兜底策略：对完整IP取hash
            return Math.abs(hostAddress.hashCode()) % 1024;
        } catch (UnknownHostException e) {
            // 若无法获取IP，使用默认值（需确保生产环境不会发生）
            return 0L;
        }
    }
}
