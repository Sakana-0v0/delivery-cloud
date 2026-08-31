package com.sakana.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sakana.dao.entity.UserEventOutbox;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户事件 outbox Mapper
 */
@Mapper
public interface UserEventOutboxMapper extends BaseMapper<UserEventOutbox> {
}
