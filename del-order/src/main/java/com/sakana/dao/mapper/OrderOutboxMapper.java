package com.sakana.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sakana.dao.entity.OrderOutbox;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单事件 outbox Mapper
 */
@Mapper
public interface OrderOutboxMapper extends BaseMapper<OrderOutbox> {
}
