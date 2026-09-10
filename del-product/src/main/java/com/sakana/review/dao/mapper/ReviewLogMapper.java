package com.sakana.review.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sakana.review.dao.entity.ReviewLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 评价事件日志 Mapper（t_review_log）
 *
 * <p>此表为 append-only，只支持 INSERT 和 SELECT，不支持 UPDATE 和 DELETE
 */
@Mapper
public interface ReviewLogMapper extends BaseMapper<ReviewLog> {

    /**
     * 插入事件日志
     *
     * @param reviewLog 事件日志实体
     * @return 影响行数（通常为1）
     */
    int insertLog(@Param("reviewLog") ReviewLog reviewLog);
}
