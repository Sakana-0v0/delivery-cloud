package com.sakana.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sakana.dao.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 订单 Mapper（含库存回滚/扣减等原子操作）
 */
@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    /**
     * 扣减库存（乐观锁防超卖，下单用）
     */
    @Update("UPDATE t_product SET stock = stock - #{quantity} " +
            "WHERE id = #{productId} AND stock >= #{quantity} AND is_deleted = 0")
    int decreaseStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    /**
     * 回滚库存（取消/超时用）
     */
    @Update("UPDATE t_product SET stock = stock + #{quantity} " +
            "WHERE id = #{productId} AND is_deleted = 0")
    int increaseStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);
}
