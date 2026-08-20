package com.sakana.configs;


import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClientConfiguration;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;



@Configuration
//选用自定义的负载均衡配置类  --> 自定义负载均衡策略
@LoadBalancerClients(value= {
         @LoadBalancerClient(name = "idGenerator", configuration = LoadBalancedConfig.class),}
         ,defaultConfiguration= LoadBalancerClientConfiguration.class
)
public class RestTemplateConfig {

    @Bean
    // @LoadBalanced 赋予了负载均衡的能力，使得RestTemplate具备了通过服务名调用并根据负载均衡策略选择一个服务实例的能力
    @LoadBalanced
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

}
