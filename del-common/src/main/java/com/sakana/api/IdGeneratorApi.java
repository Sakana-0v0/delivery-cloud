package com.sakana.api;


import com.sakana.web.vo.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;


@Configuration
@FeignClient(name = "cloudIdGenerator")
public interface IdGeneratorApi {

    @GetMapping("/hello")
    public String sayHello() ;


    @GetMapping("/id")
    public String generateId() ;



    @GetMapping("/next/batch")
    public R nextBatch(@RequestParam(value = "size", defaultValue = "10")  int size  );


}

