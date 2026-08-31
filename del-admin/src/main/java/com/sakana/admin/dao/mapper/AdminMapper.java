package com.sakana.admin.dao.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sakana.admin.dao.entity.Admin;
import org.apache.ibatis.annotations.Mapper;

/**
 * 管理员 Mapper
 */
@DS("admin")
@Mapper
public interface AdminMapper extends BaseMapper<Admin> {
}
