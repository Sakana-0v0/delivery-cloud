package com.sakana.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sakana.dao.entity.VisitLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 访问日志 Mapper
 */
@Mapper
public interface VisitLogMapper extends BaseMapper<VisitLog> {
}