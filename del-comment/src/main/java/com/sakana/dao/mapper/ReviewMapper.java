package com.sakana.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import com.sakana.dao.entity.Review;
import com.sakana.dto.ReviewCountDTO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * 商品售后评价 Mapper
 */
@Mapper
public interface ReviewMapper extends BaseMapper<Review> {

    /**
     * 物理删除评价（取消评价时使用）。
     * <p>
     * 不能用 MyBatis-Plus 的逻辑删除：逻辑删除会把记录置 is_deleted=1，
     * 而唯一键 (order_id, product_id) 不含 is_deleted，残留记录会阻塞后续重新评价。
     */
    @Delete("DELETE FROM t_review WHERE id = #{id}")
    int physicalDeleteById(@Param("id") Long id);

    /**
     * 按商品聚合赞/踩计数（跨订单、跨用户全局统计，供商品列表/详情只读展示）
     *
     * @return 每个 (productId, type) 一条，type=1 赞 / 2 踩
     */
    @Select("<script>SELECT product_id AS productId, type AS type, COUNT(*) AS cnt " +
            "FROM t_review WHERE is_deleted = 0 AND product_id IN " +
            "<foreach collection='productIds' item='pid' open='(' separator=',' close=')'>#{pid}</foreach> " +
            "GROUP BY product_id, type</script>")
    List<ReviewCountDTO> countGroupByProductAndType(@Param("productIds") Collection<Long> productIds);
}
