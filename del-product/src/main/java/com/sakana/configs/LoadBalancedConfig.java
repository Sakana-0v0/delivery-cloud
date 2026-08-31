package com.sakana.configs;

import lombok.extern.java.Log;
import org.springframework.cloud.loadbalancer.core.RandomLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import org.springframework.cloud.loadbalancer.core.RoundRobinLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@Log
public class LoadBalancedConfig {
    @Bean
    public ReactorServiceInstanceLoadBalancer reactorServiceInstanceLoadBalancer(Environment environment,
                                                                                 LoadBalancerClientFactory loadBalancerClientFactory ){
        String name = environment.getProperty(LoadBalancerClientFactory.PROPERTY_NAME);
        log.info("获取到的name:"+ name);
        return new RandomLoadBalancer(
               loadBalancerClientFactory.getLazyProvider(name, ServiceInstanceListSupplier.class), name);

//        return new RoundRobinLoadBalancer(loadBalancerClientFactory.getLazyProvider(name, ServiceInstanceListSupplier.class), name);
    }


}
