# PV/UV/客户端统计功能 - 开发方案

**修订日期**：2026-08-31  
**适用版本**：delivery-cloud 当前版本  
**预计工时**：1.5 小时

---

## 一、项目现状

### 1.1 当前服务状态

| 服务 | 端口 | 状态 | 备注 |
|------|------|------|------|
| del-gateway | 10008 | 正常 | API入口 |
| del-user | 10003 | 正常 | 用户服务 |
| del-product | 10000 | 正常 | 商品服务 |
| del-order | 10001 | 正常 | 订单服务 |
| del-payment | 10004 | 正常 | 支付服务 |
| del-message | 10002 | 正常 | 消息服务 |
| del-stats | 10009 | 正常 | 统计服务 |
| del-admin | 10005 | 正常 | 管理后台 |

### 1.2 技术栈

| 组件 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.x | 基础框架 |
| MyBatis Plus | 3.5.x | ORM |
| Nacos | 2.x | 配置中心 |
| MySQL | 8.x | 数据库 |

### 1.3 del-stats 服务结构

**现有目录**：
```
del-stats/src/main/java/com/sakana/
├── StatsApplication.java          # 启动类
├── configs/                       # 配置类
├── dao/
│   ├── entity/                   # 实体类
│   └── mapper/                  # Mapper
├── enums/                        # 枚举
├── feign/                        # Feign客户端
│   ├── dto/
│   └── fallback/
├── services/
│   ├── impl/
│   └── StatsService.java
└── web/
    └── controllers/
```

---

## 二、功能需求

### 2.1 需求描述

实现网站访问统计功能，记录：
- **PV（Page View）**：页面访问量
- **UV（Unique Visitor）**：独立访客数
- **客户端分布**：PC / Mobile / Other 占比

### 2.2 实现位置

**服务**：del-stats（端口 10009）  
**数据库**：del_stats_db

---

## 三、数据库设计

### 3.1 建表 SQL

在 MySQL `del_stats_db` 中执行：

```sql
CREATE TABLE IF NOT EXISTS `t_visit_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `visitor_id` VARCHAR(64) DEFAULT NULL COMMENT '访客ID',
  `user_id` BIGINT DEFAULT NULL COMMENT '登录用户ID',
  `page` VARCHAR(128) NOT NULL COMMENT '访问页面路径',
  `client_type` VARCHAR(32) DEFAULT NULL COMMENT '客户端类型：PC/Mobile/Android/iOS',
  `ip` VARCHAR(64) DEFAULT NULL COMMENT 'IP地址',
  `user_agent` VARCHAR(512) DEFAULT NULL COMMENT 'User-Agent',
  `referer` VARCHAR(256) DEFAULT NULL COMMENT '来源页面',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '访问时间',
  PRIMARY KEY (`id`),
  INDEX `idx_create_time` (`create_time`),
  INDEX `idx_page` (`page`),
  INDEX `idx_visitor_id` (`visitor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='访问日志表';
```

---

## 四、文件清单

| 序号 | 文件路径 | 说明 | 操作 |
|------|----------|------|------|
| 1 | `del-stats/src/main/java/com/sakana/dao/entity/VisitLog.java` | 访问日志实体 | 新增 |
| 2 | `del-stats/src/main/java/com/sakana/dao/mapper/VisitLogMapper.java` | Mapper | 新增 |
| 3 | `del-stats/src/main/java/com/sakana/services/VisitLogService.java` | 服务接口 | 新增 |
| 4 | `del-stats/src/main/java/com/sakana/services/impl/VisitLogServiceImpl.java` | 服务实现 | 新增 |
| 5 | `del-stats/src/main/java/com/sakana/configs/AccessLogInterceptor.java` | 访问拦截器 | 新增 |
| 6 | `del-stats/src/main/java/com/sakana/configs/WebMvcConfig.java` | 拦截器注册 | 新增 |
| 7 | `del-stats/src/main/java/com/sakana/web/controllers/VisitStatsController.java` | 统计API | 新增 |

---

## 五、代码实现

### 5.1 实体类 VisitLog.java

**路径**：`del-stats/src/main/java/com/sakana/dao/entity/VisitLog.java`

```java
package com.sakana.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_visit_log")
public class VisitLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String visitorId;
    private Long userId;
    private String page;
    private String clientType;
    private String ip;
    private String userAgent;
    private String referer;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
```

---

### 5.2 Mapper VisitLogMapper.java

**路径**：`del-stats/src/main/java/com/sakana/dao/mapper/VisitLogMapper.java`

```java
package com.sakana.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sakana.dao.entity.VisitLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface VisitLogMapper extends BaseMapper<VisitLog> {
}
```

---

### 5.3 服务接口 VisitLogService.java

**路径**：`del-stats/src/main/java/com/sakana/services/VisitLogService.java`

```java
package com.sakana.services;

import com.sakana.dao.entity.VisitLog;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface VisitLogService {
    void saveLog(VisitLog log);
    Long getPv(LocalDate date);
    Long getUv(LocalDate date);
    Map<String, Long> getClientStats(LocalDate date);
    Long getPvRange(LocalDate start, LocalDate end);
    Long getUvRange(LocalDate start, LocalDate end);
    List<Map<String, Object>> getDailyStats(LocalDate start, LocalDate end);
}
```

---

### 5.4 服务实现 VisitLogServiceImpl.java

**路径**：`del-stats/src/main/java/com/sakana/services/impl/VisitLogServiceImpl.java`

```java
package com.sakana.services.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sakana.dao.entity.VisitLog;
import com.sakana.dao.mapper.VisitLogMapper;
import com.sakana.services.VisitLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class VisitLogServiceImpl implements VisitLogService {
    
    private final VisitLogMapper visitLogMapper;
    
    @Override
    public void saveLog(VisitLog log) {
        visitLogMapper.insert(log);
    }
    
    @Override
    public Long getPv(LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.atTime(LocalTime.MAX);
        
        return visitLogMapper.selectCount(
            new LambdaQueryWrapper<VisitLog>()
                .between(VisitLog::getCreateTime, start, end)
        );
    }
    
    @Override
    public Long getUv(LocalDate date) {
        // 使用 GROUP BY visitor_id 统计 UV
        return null; // 待实现
    }
    
    @Override
    public Map<String, Long> getClientStats(LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.atTime(LocalTime.MAX);
        
        List<VisitLog> logs = visitLogMapper.selectList(
            new LambdaQueryWrapper<VisitLog>()
                .between(VisitLog::getCreateTime, start, end)
        );
        
        Map<String, Long> stats = new HashMap<>();
        stats.put("PC", 0L);
        stats.put("Mobile", 0L);
        stats.put("Other", 0L);
        
        for (VisitLog log : logs) {
            String type = log.getClientType() != null ? log.getClientType() : "Other";
            stats.merge(type, 1L, Long::sum);
        }
        
        return stats;
    }
    
    @Override
    public Long getPvRange(LocalDate start, LocalDate end) {
        LocalDateTime startTime = start.atStartOfDay();
        LocalDateTime endTime = end.atTime(LocalTime.MAX);
        
        return visitLogMapper.selectCount(
            new LambdaQueryWrapper<VisitLog>()
                .between(VisitLog::getCreateTime, startTime, endTime)
        );
    }
    
    @Override
    public Long getUvRange(LocalDate start, LocalDate end) {
        return null;
    }
    
    @Override
    public List<Map<String, Object>> getDailyStats(LocalDate start, LocalDate end) {
        return new ArrayList<>();
    }
}
```

---

### 5.5 访问拦截器 AccessLogInterceptor.java

**路径**：`del-stats/src/main/java/com/sakana/configs/AccessLogInterceptor.java`

```java
package com.sakana.configs;

import cn.hutool.core.util.IdUtil;
import com.sakana.dao.entity.VisitLog;
import com.sakana.services.VisitLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccessLogInterceptor implements HandlerInterceptor {
    
    private final VisitLogService visitLogService;
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        saveAccessLog(request);
        return true;
    }
    
    @Async
    public void saveAccessLog(HttpServletRequest request) {
        try {
            VisitLog visitLog = new VisitLog();
            visitLog.setVisitorId(getOrCreateVisitorId(request));
            visitLog.setPage(request.getRequestURI());
            visitLog.setClientType(parseClientType(request.getHeader("User-Agent")));
            visitLog.setIp(getClientIp(request));
            visitLog.setUserAgent(truncate(request.getHeader("User-Agent"), 512));
            visitLog.setReferer(truncate(request.getHeader("Referer"), 256));
            
            visitLogService.saveLog(visitLog);
        } catch (Exception e) {
            log.error("记录访问日志失败", e);
        }
    }
    
    private String getOrCreateVisitorId(HttpServletRequest request) {
        String visitorId = request.getHeader("X-Visitor-Id");
        if (visitorId == null || visitorId.isEmpty()) {
            visitorId = IdUtil.fastSimpleUUID();
        }
        return visitorId;
    }
    
    private String parseClientType(String userAgent) {
        if (userAgent == null) return "Other";
        String ua = userAgent.toLowerCase();
        if (ua.contains("mobile") || ua.contains("android") || ua.contains("iphone")) {
            return "Mobile";
        } else if (ua.contains("windows") || ua.contains("mac") || ua.contains("linux")) {
            return "PC";
        }
        return "Other";
    }
    
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
    
    private String truncate(String str, int maxLength) {
        if (str == null) return null;
        return str.length() > maxLength ? str.substring(0, maxLength) : str;
    }
}
```

---

### 5.6 拦截器注册 WebMvcConfig.java

**路径**：`del-stats/src/main/java/com/sakana/configs/WebMvcConfig.java`

```java
package com.sakana.configs;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
@EnableAsync
public class WebMvcConfig implements WebMvcConfigurer {
    
    private final AccessLogInterceptor accessLogInterceptor;
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(accessLogInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                    "/actuator/**",
                    "/internal/**"
                );
    }
}
```

---

### 5.7 统计 API VisitStatsController.java

**路径**：`del-stats/src/main/java/com/sakana/web/controllers/VisitStatsController.java`

```java
package com.sakana.web.controllers;

import com.sakana.services.VisitLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/internal/stats/visit")
@RequiredArgsConstructor
public class VisitStatsController {
    
    private final VisitLogService visitLogService;
    
    @GetMapping("/today")
    public Map<String, Object> getTodayStats() {
        LocalDate today = LocalDate.now();
        
        Map<String, Object> result = new HashMap<>();
        result.put("date", today.toString());
        result.put("pv", visitLogService.getPv(today));
        result.put("uv", visitLogService.getUv(today));
        result.put("clientStats", visitLogService.getClientStats(today));
        
        return result;
    }
    
    @GetMapping("/range")
    public Map<String, Object> getRangeStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        
        Map<String, Object> result = new HashMap<>();
        result.put("startDate", startDate.toString());
        result.put("endDate", endDate.toString());
        result.put("pv", visitLogService.getPvRange(startDate, endDate));
        result.put("uv", visitLogService.getUvRange(startDate, endDate));
        result.put("dailyStats", visitLogService.getDailyStats(startDate, endDate));
        
        return result;
    }
}
```

---

### 5.8 添加 EnableAsync 注解

**修改文件**：`del-stats/src/main/java/com/sakana/StatsApplication.java`

在类上添加 `@EnableAsync` 注解

---

## 六、API 文档

### 6.1 今日统计

**端点**：`GET /internal/stats/visit/today`  
**认证**：`X-Internal-Service-Token: internal-service-secret-key-2024`

**响应示例**：
```json
{
  "date": "2026-08-31",
  "pv": 12345,
  "uv": 2345,
  "clientStats": {
    "PC": 8000,
    "Mobile": 4000,
    "Other": 345
  }
}
```

### 6.2 日期范围统计

**端点**：`GET /internal/stats/visit/range?startDate=2026-08-01&endDate=2026-08-31`  

---

## 七、执行清单

| 序号 | 步骤 | 操作 |
|------|------|------|
| 1 | 创建表 | 在 del_stats_db 执行建表 SQL |
| 2 | 创建实体 | 新增 VisitLog.java |
| 3 | 创建 Mapper | 新增 VisitLogMapper.java |
| 4 | 创建服务接口 | 新增 VisitLogService.java |
| 5 | 创建服务实现 | 新增 VisitLogServiceImpl.java |
| 6 | 创建拦截器 | 新增 AccessLogInterceptor.java |
| 7 | 注册拦截器 | 新增 WebMvcConfig.java |
| 8 | 添加 EnableAsync | 修改 StatsApplication.java |
| 9 | 创建 Controller | 新增 VisitStatsController.java |
| 10 | 编译测试 | mvn compile -pl del-stats -am |
| 11 | 重启服务 | 重启 del-stats |

---

## 八、验证步骤

### 8.1 编译验证

```bash
cd E:\Idea_project\delivery-cloud
mvn compile -pl del-stats -am
```

### 8.2 API 测试

```bash
curl -X GET "http://localhost:10009/internal/stats/visit/today" -H "X-Internal-Service-Token: internal-service-secret-key-2024"
```

---

## 九、注意事项

1. **异步处理**：访问日志使用 @Async 异步保存，不影响接口响应
2. **异常捕获**：拦截器中已做异常处理，失败不影响主流程
3. **数据量**：高并发场景下可考虑批量插入或消息队列

---

**文档结束**
