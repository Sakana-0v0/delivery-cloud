package com.sakana.configs;

import com.sakana.dao.entity.VisitLog;
import com.sakana.services.VisitLogService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 访问日志拦截器
 *
 * <p>拦截所有请求，记录访问日志。客户端类型根据 User-Agent 判断。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccessLogInterceptor implements HandlerInterceptor {
    
    private final VisitLogService visitLogService;
    
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        try {
            VisitLog visitLog = new VisitLog();
            
            // 获取或生成访客ID
            String visitorId = getVisitorId(request);
            visitLog.setVisitorId(visitorId);
            
            // 设置用户ID（如果已登录，需要从其他地方获取）
            // 这里暂时留空，后续可通过 SecurityContext 获取
            visitLog.setUserId(null);
            
            // 访问页面
            visitLog.setPage(request.getRequestURI());
            
            // 判断客户端类型
            visitLog.setClientType(getClientType(request));
            
            // IP 地址
            visitLog.setIp(getClientIp(request));
            
            // User-Agent
            visitLog.setUserAgent(truncate(request.getHeader("User-Agent"), 512));
            
            // 来源页面
            visitLog.setReferer(truncate(request.getHeader("Referer"), 256));
            
            // 访问时间
            visitLog.setCreateTime(LocalDateTime.now());
            
            // 异步保存日志
            visitLogService.saveLog(visitLog);
            
        } catch (Exception e) {
            // 记录日志失败不影响主流程
            log.error("记录访问日志异常", e);
        }
    }
    
    /**
     * 获取或生成访客ID
     * 优先从 Cookie 中获取，若无则生成 UUID 并在响应中设置
     */
    private String getVisitorId(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("visitor_id".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        // 生成新的访客ID
        return UUID.randomUUID().toString().replace("-", "");
    }
    
    /**
     * 根据 User-Agent 判断客户端类型
     */
    private String getClientType(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null) {
            return "Other";
        }
        userAgent = userAgent.toLowerCase();
        
        if (userAgent.contains("windows") || userAgent.contains("macintosh") || userAgent.contains("linux")) {
            return "PC";
        } else if (userAgent.contains("mobile") || userAgent.contains("android")) {
            return "Mobile";
        } else if (userAgent.contains("iphone") || userAgent.contains("ipad")) {
            return "iOS";
        } else if (userAgent.contains("android")) {
            return "Android";
        }
        return "Other";
    }
    
    /**
     * 获取客户端真实 IP
     * 兼容反向代理场景
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多级代理时取第一个 IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
    
    /**
     * 截断字符串到最大长度
     */
    private String truncate(String str, int maxLength) {
        if (str == null) return null;
        return str.length() > maxLength ? str.substring(0, maxLength) : str;
    }
}