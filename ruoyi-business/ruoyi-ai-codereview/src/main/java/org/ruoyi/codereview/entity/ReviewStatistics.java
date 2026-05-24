package org.ruoyi.codereview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 审查统计实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cr_review_statistics")
public class ReviewStatistics extends TenantEntityLocalDateTime {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 项目名称
     */
    private String projectName;

    /**
     * 平台
     */
    private String platform;

    /** 统计日期 */
    private LocalDate statDate;

    /** 总审查次数 */
    private Integer totalReviews;

    /** 总文件数 */
    private Integer totalFiles;

    /** 总新增行数 */
    private Integer totalAdditions;

    /** 总删除行数 */
    private Integer totalDeletions;

    /** 平均分数 */
    private BigDecimal avgScore;

    /** 最高分 */
    private Integer maxScore;

    /** 最低分 */
    private Integer minScore;

    /** 通过次数 */
    private Integer passCount;

    /** 不通过次数 */
    private Integer failCount;

    /** 功能性平均分 */
    private BigDecimal functionalityAvg;

    /** 安全性平均分 */
    private BigDecimal securityAvg;

    /** 最佳实践平均分 */
    private BigDecimal bestPracticeAvg;

    /** 性能平均分 */
    private BigDecimal performanceAvg;

    /** 提交信息平均分 */
    private BigDecimal commitMessageAvg;
}
