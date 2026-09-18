package com.sakana.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sakana.dao.entity.FreeOrderCoupon;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface FreeOrderCouponMapper extends BaseMapper<FreeOrderCoupon> {

    /**
     * 原子化抢单：UPDATE ... WHERE id = ? AND status = ''AVAILABLE''
     * 返回被更新行数（1=成功，0=没名额了）
     */
    @Update("UPDATE t_free_order_coupon " +
            "SET status = 'GRABBED', grabbed_user_id = #{userId}, grabbed_time = NOW() " +
            "WHERE id = #{id} AND status = 'AVAILABLE'")
    int atomicGrab(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 原子化使用免单码：UPDATE ... WHERE code = ? AND status = ''GRABBED'' AND grabbed_user_id = ?
     * 返回被更新行数（1=成功，0=无效或已被使用）
     */
    @Update("UPDATE t_free_order_coupon " +
            "SET status = 'USED', used_order_id = #{orderId}, used_time = NOW() " +
            "WHERE code = #{code} AND status = 'GRABBED' AND grabbed_user_id = #{userId}")
    int atomicUse(@Param("code") String code,
                  @Param("userId") Long userId,
                  @Param("orderId") Long orderId);

    /**
     * ★ P1-批量预热：单条 SQL 批量插入免单码（#FREE-ORDER-006）。
     *
     * <p>用 MySQL 的多值 INSERT 语法 VALUES (a,b),(c,d),(e,f)，一次 SQL 写入多行，
     * 避免 N 次 round-trip。调用方应自行 chunk（建议 500~1000/批），
     * 控制单条 SQL 大小与 max_allowed_packet 上限。
     *
     * <p>本方法不返回 affected rows（MyBatis 多值 INSERT 返回可能为负数），
     * 调用方按 chunk 大小估算即可。
     *
     * @param coupons 待插入免单码列表（建议 500~1000 条）
     */
    @Insert({
        "<script>",
        "INSERT INTO t_free_order_coupon (activity_id, code, max_amount, status, created_at, updated_at) VALUES ",
        "<foreach collection='list' item='c' separator=','>",
        "(#{c.activityId}, #{c.code}, #{c.maxAmount}, #{c.status}, NOW(6), NOW(6))",
        "</foreach>",
        "</script>"
    })
    int batchInsert(@Param("list") List<FreeOrderCoupon> coupons);
}
