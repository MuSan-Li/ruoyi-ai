package org.ruoyi.codereview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 项目审查配置实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cr_project_config")
public class ProjectConfig extends TenantEntityLocalDateTime {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 项目名称
     */
    private String projectName;

    /**
     * 平台: gitlab, github, gitea
     */
    private String platform;

    /**
     * 平台项目ID
     */
    private String projectId;

    /**
     * 平台服务器URL
     */
    private String platformUrl;

    /**
     * 平台访问Token（敏感字段，日志中脱敏）
     */
    @ToString.Exclude
    private String platformToken;

    /**
     * Webhook 密钥（敏感字段，日志中脱敏）
     */
    @ToString.Exclude
    private String webhookSecret;

    /**
     * 使用的模型ID
     */
    private Long modelId;

    /**
     * 是否启用审查
     */
    private Integer reviewEnabled;

    /**
     * 是否启用Push审查
     */
    private Integer pushReviewEnabled;

    /** 仅审查受保护分支 */
    private Integer protectedBranchesOnly;

    /** 审查文件扩展名 */
    private String fileExtensions;

    /** 排除文件模式 */
    private String excludePatterns;

    /** 单次最大审查文件数 */
    private Integer maxFilesPerReview;

    /**
     * 最大Token数
     */
    private Integer maxTokens;

    /**
     * 审查风格
     */
    private String reviewStyle;

    /**
     * 通过分数
     */
    private Integer passScore;

    /**
     * 通知渠道(JSON)
     */
    private String notificationChannels;

    /**
     * 自定义规则(JSON)
     */
    private String customRules;

    /**
     * 获取脱敏后的 Token（用于日志显示）
     */
    public String getMaskedToken() {
        if (platformToken == null || platformToken.length() < 8) {
            return "****";
        }
        return platformToken.substring(0, 4) + "****" + platformToken.substring(platformToken.length() - 4);
    }

    /**
     * 检查是否有有效的平台配置
     */
    public boolean hasPlatformConfig() {
        return platformUrl != null && !platformUrl.isEmpty()
            && platformToken != null && !platformToken.isEmpty();
    }
}
