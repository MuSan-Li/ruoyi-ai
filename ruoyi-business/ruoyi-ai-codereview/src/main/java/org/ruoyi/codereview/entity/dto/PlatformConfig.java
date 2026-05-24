package org.ruoyi.codereview.entity.dto;

import lombok.Data;

/**
 * 平台配置 DTO
 * <p>
 * 用于封装 Git 平台连接配置
 */
@Data
public class PlatformConfig {

    /**
     * 平台类型: gitlab, github, gitea
     */
    private String platform;

    /**
     * 平台服务器 URL
     */
    private String url;

    /**
     * 访问 Token
     */
    private String token;

    /**
     * Webhook 密钥
     */
    private String webhookSecret;

    /**
     * 是否启用
     */
    private boolean enabled = true;

    /**
     * 从项目配置创建
     */
    public static PlatformConfig fromProjectConfig(String platform, String url, String token, String webhookSecret) {
        PlatformConfig config = new PlatformConfig();
        config.setPlatform(platform);
        config.setUrl(url);
        config.setToken(token);
        config.setWebhookSecret(webhookSecret);
        config.setEnabled(url != null && !url.isEmpty() && token != null && !token.isEmpty());
        return config;
    }
}
