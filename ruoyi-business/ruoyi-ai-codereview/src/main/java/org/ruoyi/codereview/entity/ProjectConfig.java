package org.ruoyi.codereview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 项目审查配置实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cr_project_config")
public class ProjectConfig extends TenantEntityLocalDateTime {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目名称 */
    private String projectName;

    /** 平台 */
    private String platform;

    /** 平台项目ID */
    private String projectId;

    /** 使用的模型ID */
    private Long modelId;

    /** 是否启用审查 */
    private Integer reviewEnabled;

    /** 是否启用Push审查 */
    private Integer pushReviewEnabled;

    /** 仅审查受保护分支 */
    private Integer protectedBranchesOnly;

    /** 审查文件扩展名 */
    private String fileExtensions;

    /** 排除文件模式 */
    private String excludePatterns;

    /** 单次最大审查文件数 */
    private Integer maxFilesPerReview;

    /** 最大Token数 */
    private Integer maxTokens;

    /** 审查风格 */
    private String reviewStyle;

    /** 通过分数 */
    private Integer passScore;

    /** 通知渠道 */
    private String notificationChannels;

    /** 自定义规则(JSON) */
    private String customRules;
}
