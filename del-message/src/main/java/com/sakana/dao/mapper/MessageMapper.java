package com.sakana.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sakana.dao.entity.Message;
import org.apache.ibatis.annotations.Mapper;

/**
 * 消息 Mapper
 */
@Mapper
public interface MessageMapper extends BaseMapper<Message> {
}
