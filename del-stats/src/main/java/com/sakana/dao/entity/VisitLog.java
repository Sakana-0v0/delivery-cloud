package com.sakana.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 访问日志实体
 *
 * <p>记录用户访问页面时的相关信息，用于 PV/UV 统计
 */
@Data
@TableName("t_visit_log")
public class VisitLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /** 访客ID（基于 Cookie/UUID 生成） */
    private String visitorId;
    
    /** 登录用户ID（未登录为 null） */
    private Long userId;
    
    /** 访问页面路径 */
    private String page;
    
    /** 客户端类型：PC/Mobile/Android/iOS */
    private String clientType;
    
    /** 客户端 IP 地址 */
    private String ip;
    
    /** User-Agent */
    private String userAgent;
    
    /** 来源页面 */
    private String referer;
    
    /** 访问时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}