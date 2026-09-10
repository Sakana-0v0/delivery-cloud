package com.sakana.configs;


import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;



/**
 * RestTemplate 公共配置
 *
 * <h2>两个 Bean</h2>
 * <ul>
 *   <li>{@code restTemplate}（{@link LoadBalanced}）：用于微服务间调用（serviceId 走 Nacos）</li>
 *   <li>{@code externalRestTemplate}（普通）：用于调用外部第三方 API（如 QQ、支付宝、DashScope）</li>
 * </ul>
 *
 * <p>所有依赖 del-common 的微服务自动获得这两个 Bean。
 *
 * <h2>使用方式</h2>
 * <pre>
 * // 微服务间调用
 * &#064;Autowired
 * private RestTemplate restTemplate;
 *
 * // 外部 API 调用
 * &#064;Autowired
 * &#064;Qualifier("externalRestTemplate")
 * private RestTemplate externalRestTemplate;
 * </pre>
 *
 * @author sakana
 * @since 2026-09-05
 */
@Configuration
public class RestTemplateConfig {

    /**
     * 微服务间调用的 RestTemplate（带 LoadBalancer）
     */
    @Bean
    @LoadBalanced
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    /**
     * 外部 API 调用的 RestTemplate（不带 LoadBalancer）
     * <p>
     * 用于调用第三方服务（QQ、支付宝、DashScope 等），
     * 这些域名不在 Nacos 注册，必须用普通 HTTP 客户端
     */
    @Bean("externalRestTemplate")
    public RestTemplate externalRestTemplate() {
        return new RestTemplate();
    }
}
