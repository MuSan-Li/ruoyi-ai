package org.ruoyi.codereview.entity.dto;

import lombok.Data;

import java.util.List;

/**
 * 审查结果 DTO
 */
@Data
public class ReviewResultDto {

    /** 审查结果文本 */
    private String reviewText;

    /** 总分 (0-100) */
    private int score;

    /** 评分等级 */
    private String scoreLevel;

    /** 是否通过 */
    private boolean passed;

    /** 各维度得分 */
    private List<DimensionScore> dimensionScores;

    /** 主要问题列表 */
    private List<String> mainIssues;

    /** 改进建议列表 */
    private List<String> suggestions;

    @Data
    public static class DimensionScore {
        /** 维度名称 */
        private String name;
        /** 得分 */
        private int score;
        /** 满分 */
        private int maxScore;
        /** 权重 */
        private int weight;
    }
}
