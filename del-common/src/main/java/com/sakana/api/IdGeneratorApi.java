package com.sakana.api;


import com.sakana.web.vo.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;


/**
 * ID 生成器 Feign Client
 *
 * <p>使用 contextId 避免多模块引入时 Bean 名称冲突
 */
@FeignClient(name = "cloudIdGenerator", contextId = "cloudIdGeneratorApi")
public interface IdGeneratorApi {

    @GetMapping("/hello")
    public String sayHello() ;


    @GetMapping("/id")
    public String generateId() ;



    @GetMapping("/next/batch")
    public R nextBatch(@RequestParam(value = "size", defaultValue = "10")  int size  );


}
