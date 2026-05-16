package org.ruoyi.codereview.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 代码审查配置属性
 */
@Data
@ConfigurationProperties(prefix = "codereview")
public class CodeReviewProperties {

    /**
     * 是否启用
     */
    private boolean enabled = true;

    /**
     * LLM 配置
     */
    private LlmConfig llm = new LlmConfig();

    /**
     * 平台配置
     */
    private PlatformConfig platform = new PlatformConfig();

    /**
     * 通知配置
     */
    private NotifyConfig notify = new NotifyConfig();

    /**
     * 审查配置
     */
    private ReviewConfig review = new ReviewConfig();

    @Data
    public static class LlmConfig {
        /**
         * 框架模型管理中的模型名称
         */
        private String modelName = "deepseek-chat";
    }

    @Data
    public static class PlatformConfig {
        private GitLabConfig gitlab = new GitLabConfig();
        private GitHubConfig github = new GitHubConfig();
        private GiteaConfig gitea = new GiteaConfig();
    }

    @Data
    public static class GitLabConfig {
        private boolean enabled = true;
        private String url;
        private String token;
        private String webhookSecret;
    }

    @Data
    public static class GitHubConfig {
        private boolean enabled = true;
        private String url = "https://api.github.com";
        private String token;
        private String webhookSecret;
    }

    @Data
    public static class GiteaConfig {
        private boolean enabled = true;
        private String url;
        private String token;
        private String webhookSecret;
    }

    @Data
    public static class NotifyConfig {
        private DingTalkConfig dingtalk = new DingTalkConfig();
        private WeComConfig wecom = new WeComConfig();
        private FeishuConfig feishu = new FeishuConfig();
        private String extraWebhookUrl;
    }

    @Data
    public static class DingTalkConfig {
        private boolean enabled;
        private String webhookUrl;
        private boolean secretEnabled;
        private String secret;
    }

    @Data
    public static class WeComConfig {
        private boolean enabled;
        private String webhookUrl;
    }

    @Data
    public static class FeishuConfig {
        private boolean enabled;
        private String webhookUrl;
    }

    @Data
    public static class ReviewConfig {
        private String extensions = ".c,.cc,.cpp,.cs,.css,.cxx,.go,.h,.hh,.hpp,.hxx,.java,.js,.jsx,.md,.php,.py,.sql,.ts,.tsx,.vue,.yml";
        private int maxTokens = 10000;
        private String style = "professional";
        private boolean pushEnabled = true;
        private boolean mergeReviewOnlyProtectedBranches = false;
    }
}