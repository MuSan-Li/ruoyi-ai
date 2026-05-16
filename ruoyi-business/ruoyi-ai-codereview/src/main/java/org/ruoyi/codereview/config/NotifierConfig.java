package org.ruoyi.codereview.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.ruoyi.codereview.notifier.*;

/**
 * 通知器配置
 * <p>
 * 通过 @Import 由 CodeReviewAutoConfiguration 导入，无需 @Configuration 注解
 */
@Slf4j
public class NotifierConfig {

    @Bean
    @ConditionalOnProperty(prefix = "codereview.notify.dingtalk", name = "enabled", havingValue = "true")
    public Notifier dingTalkNotifier(CodeReviewProperties properties) {
        return new DingTalkNotifier(
                properties.getNotify().getDingtalk().getWebhookUrl(),
                properties.getNotify().getDingtalk().isSecretEnabled(),
                properties.getNotify().getDingtalk().getSecret()
        );
    }

    @Bean
    @ConditionalOnProperty(prefix = "codereview.notify.wecom", name = "enabled", havingValue = "true")
    public Notifier weComNotifier(CodeReviewProperties properties) {
        return new WeComNotifier(properties.getNotify().getWecom().getWebhookUrl());
    }

    @Bean
    @ConditionalOnProperty(prefix = "codereview.notify.feishu", name = "enabled", havingValue = "true")
    public Notifier feishuNotifier(CodeReviewProperties properties) {
        return new FeishuNotifier(properties.getNotify().getFeishu().getWebhookUrl());
    }

    // 注意：extra-webhook-url 配置已移除，因为用户不需要自定义 webhook
    // 如需启用，请在配置文件中设置 codereview.notify.extra-webhook-url 并取消注释以下代码
    // @Bean
    // public Notifier webhookNotifier(CodeReviewProperties properties) {
    //     String url = properties.getNotify().getExtraWebhookUrl();
    //     if (url == null || url.isBlank()) {
    //         return null;
    //     }
    //     return new WebhookNotifier(url);
    // }
}
