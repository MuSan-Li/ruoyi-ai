package org.ruoyi.codereview.entity.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Push审查日志 Vo
 */
@Data
public class PushReviewLogVo {

    private Long id;

    private String tenantId;

    private String projectName;

    private String author;

    private String branch;

    private String commitMessages;

    private Integer score;

    private String reviewResult;

    private Integer additions;

    private Integer deletions;

    private String platform;

    private String projectId;

    private Long createDept;

    private Long createBy;

    private LocalDateTime createTime;

    private Long updateBy;

    private LocalDateTime updateTime;
}
