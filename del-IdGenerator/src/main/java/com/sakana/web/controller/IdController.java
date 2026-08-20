package com.sakana.web.controller;


import com.sakana.service.SnowflakeIdGenerator;
import com.sakana.web.vo.R;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.java.Log;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


import java.util.List;

@RestController
@Log
@RefreshScope
public class IdController {
    private final SnowflakeIdGenerator idGenerator;

    // 构造器注入（推荐）
    public IdController(SnowflakeIdGenerator idGenerator) {
        this.idGenerator = idGenerator;
    }

    /*
     *     分布式ID：876497638143430656
     *
     */

    @GetMapping("/hello")
    public String sayHello() {
        log.info("9999 port");
        return "Hello World!";
    }


    @GetMapping("/id")
    public String generateId() {
        log.info("generateId");
        //这里生成的ID是字符串类型，方便前端处理。
        //如果讲long类型的数据直接传输，js无法正确解析，因为js只能处理624位整数
        String id = String.valueOf(idGenerator.nextId());
        log.info(id);
        return id;
    }

    @GetMapping("/next/batch")
    @Operation(summary = "获取多个分布式唯一的ID号,它们是Long型",description = "生成多个Id号，号码格式:[ 1bit | 41bit时间戳 | 10bit机器ID（5 datacenter + 5 worker）| 12bit序列号 ]")
    public R nextBatch(@RequestParam(value = "size", defaultValue = "10")  int size  ){
        List<String> ids = idGenerator
                .nextIdBatch(size)   // List<Long>
                .stream()   //将list转为stream流
                .map(String::valueOf)    // map将每个long取出转换为字符串
                .toList();     //以list形式返回
        return R.ok(ids);
        //return R.success(  idGenerator.nextIdBatch(size));
    }





}
