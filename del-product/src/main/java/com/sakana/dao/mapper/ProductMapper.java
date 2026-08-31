package com.sakana.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sakana.dao.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 商品 Mapper
 */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    /**
     * 扣减库存（乐观锁防超卖，下单用）
     */
    @Select("UPDATE t_product SET stock = stock - #{quantity} " +
            "WHERE id = #{productId} AND stock >= #{quantity} AND is_deleted = 0")
    int decreaseStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    /**
     * 回滚库存（取消/超时用）
     */
    @Select("UPDATE t_product SET stock = stock + #{quantity} " +
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
    @Select("UPDATE t_product SET stock = #{newStock} " +
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
    @Select("UPDATE t_product SET stock = stock + #{delta} " +
            "WHERE id = #{productId} AND stock = #{expectedStock} " +
            "AND stock + #{delta} >= 0 AND is_deleted = 0")
    int adjustStockIfMatch(@Param("productId") Long productId,
                           @Param("expectedStock") int expectedStock,
                           @Param("delta") int delta);

    /**
     * 统计某分类下的有效商品数（用于删除分类前置校验）
     */
    @Select("SELECT COUNT(*) FROM t_product WHERE category_id = #{categoryId} AND is_deleted = 0")
    long countByCategoryId(@Param("categoryId") Long categoryId);

    /**
     * 查询热卖商品列表（按销量倒序）
     * <p>
     * 使用参数绑定而非字符串拼接，避免 SQL 注入风险。
     *
     * @param limit 返回数量限制
     * @return 热卖商品列表
     */
    @Select("SELECT id, category_id, name, cover, description, norm_price, real_price, " +
            "stock, sales, status, create_time, update_time, is_deleted " +
            "FROM t_product " +
            "WHERE is_deleted = 0 " +
            "ORDER BY sales DESC " +
            "LIMIT #{limit}")
    List<Product> selectHotProducts(@Param("limit") int limit);
}
