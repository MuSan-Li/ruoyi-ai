package org.ruoyi.codereview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Push审查日志
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("push_review_log")
public class PushReviewLog extends TenantEntityLocalDateTime {

    @TableId(type = IdType.AUTO)
    private Long id;

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
}
