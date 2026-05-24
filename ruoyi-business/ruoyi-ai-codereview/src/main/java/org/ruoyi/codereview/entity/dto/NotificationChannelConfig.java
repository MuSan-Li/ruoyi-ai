package org.ruoyi.codereview.entity.dto;

import lombok.Data;

/**
 * 通知渠道配置 DTO
 * <p>
 * 用于描述单个通知渠道的配置信息，
 * 可序列化为 JSON 存储在 cr_project_config.notification_channels 中
 */
@Data
public class NotificationChannelConfig {

    /**
     * 渠道类型: dingtalk, wecom, feishu, webhook
     */
    private String type;

    /**
     * Webhook URL
     */
    private String webhookUrl;

    /**
     * 钉钉签名开关（仅 dingtalk 类型有效）
     */
    private Boolean secretEnabled;

    /**
     * 钉钉签名密钥（仅 dingtalk 类型有效）
     */
    private String secret;

    /**
     * 是否启用（默认 true）
     */
    private Boolean enabled;
}
