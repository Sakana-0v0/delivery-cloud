package com.sakana.configs;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 客户端配置（用于商品序列化时重签封面 URL，避免 7 天过期失效）。
 *
 * <p>与 del-file 的 MinioConfig 相互独立：del-product 自持 MinioClient 实例，
 * 不跨服务依赖，避免 del-product 强制感知 del-file 的存在。
 */
@Configuration

public class MinioConfig {

    @Value("${minio.endpoint:http://localhost:9000}")
    private String endpoint;

    @Value("${minio.access-key:admin}")
    private String accessKey;

    @Value("${minio.secret-key:sakana013}")
    private String secretKey;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}