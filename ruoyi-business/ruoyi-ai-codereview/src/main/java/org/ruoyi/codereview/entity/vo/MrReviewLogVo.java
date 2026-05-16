package org.ruoyi.codereview.entity.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * MR审查日志 Vo
 */
@Data
public class MrReviewLogVo {

    private Long id;

    private String tenantId;

    private String projectName;

    private String author;

    private String sourceBranch;

    private String targetBranch;

    private String url;

    private String commitMessages;

    private Integer score;

    private String reviewResult;

    private Integer additions;

    private Integer deletions;

    private String lastCommitId;

    private String platform;

    private String projectId;

    private Long createDept;

    private Long createBy;

    private LocalDateTime createTime;

    private Long updateBy;

    private LocalDateTime updateTime;
}
