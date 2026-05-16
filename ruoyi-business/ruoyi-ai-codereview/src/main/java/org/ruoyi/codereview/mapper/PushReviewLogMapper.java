package org.ruoyi.codereview.mapper;

import org.apache.ibatis.annotations.Param;
import org.ruoyi.codereview.entity.PushReviewLog;
import org.ruoyi.codereview.entity.vo.PushReviewLogVo;
import org.ruoyi.common.mybatis.core.mapper.BaseMapperPlus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Push审查日志 Mapper
 */
public interface PushReviewLogMapper extends BaseMapperPlus<PushReviewLog, PushReviewLogVo> {

    /**
     * 统计平均分
     */
    Double selectAvgScore(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    /**
     * 按项目统计
     */
    List<Map<String, Object>> selectProjectStats(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
}
