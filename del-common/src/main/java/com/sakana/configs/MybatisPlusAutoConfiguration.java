package com.sakana.configs;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.time.LocalDateTime;

/**
 * MyBatis-Plus 通用配置（自动装配）
 *
 * <p>所有引入 del-common + mybatis-plus-spring-boot3-starter 的服务都会自动获得：
 * <ul>
 *   <li>SqlSessionFactory 自动配置</li>
 *   <li>分页插件（MySQL）</li>
 *   <li>create_time / update_time 自动填充</li>
 * </ul>
 *
 * @author Codex
 * @since 2026-08-25
 */
@Configuration
@ConditionalOnClass(name = "com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor")
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
public class MybatisPlusAutoConfiguration implements MetaObjectHandler {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
