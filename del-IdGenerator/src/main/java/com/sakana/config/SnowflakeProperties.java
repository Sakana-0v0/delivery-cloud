package com.sakana.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "snowflake")
public class SnowflakeProperties {
    /**
     * 数据中心ID，默认0，范围0-31
     */
    private Long datacenterId = 0L;

    /**
     * 机器ID，若不配置则根据IP自动计算
     */
    private Long workerId = 1L;

    /**
     * 起始时间戳 (默认：2020-01-01 00:00:00)
     */
    private long epoch = 1577836800000L;
}