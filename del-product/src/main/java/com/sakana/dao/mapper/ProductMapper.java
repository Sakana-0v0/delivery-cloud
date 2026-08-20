package com.sakana.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sakana.dao.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 商品 Mapper
 */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {

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

    /**
     * 管理员设置库存到指定值（CAS 乐观锁）
     *
     * @param productId     商品ID
     * @param expectedStock 期望的当前库存（CAS 值）
     * @param newStock      新库存
     * @return 影响行数，0 表示 CAS 失败（库存已被其他事务修改）
     */
    @Update("UPDATE t_product SET stock = #{newStock} " +
            "WHERE id = #{productId} AND stock = #{expectedStock} AND is_deleted = 0")
    int setStockIfMatch(@Param("productId") Long productId,
                        @Param("expectedStock") int expectedStock,
                        @Param("newStock") int newStock);

    /**
     * 管理员增减库存（CAS 乐观锁）
     *
     * @param productId     商品ID
     * @param expectedStock 期望的当前库存（CAS 值）
     * @param delta         增减量（可负）
     * @return 影响行数，0 表示 CAS 失败
     */
    @Update("UPDATE t_product SET stock = stock + #{delta} " +
            "WHERE id = #{productId} AND stock = #{expectedStock} " +
            "AND stock + #{delta} >= 0 AND is_deleted = 0")
    int adjustStockIfMatch(@Param("productId") Long productId,
                           @Param("expectedStock") int expectedStock,
                           @Param("delta") int delta);

    /**
     * 统计某分类下的有效商品数（用于删除分类前置校验）
     */
    @org.apache.ibatis.annotations.Select(
            "SELECT COUNT(*) FROM t_product WHERE category_id = #{categoryId} AND is_deleted = 0")
    long countByCategoryId(@Param("categoryId") Long categoryId);
}