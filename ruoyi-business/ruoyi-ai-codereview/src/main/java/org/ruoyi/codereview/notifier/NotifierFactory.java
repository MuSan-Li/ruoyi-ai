package org.ruoyi.codereview.notifier;

import lombok.extern.slf4j.Slf4j;
import org.ruoyi.codereview.entity.dto.NotificationChannelConfig;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 通知器工厂
 * <p>
 * 根据配置动态创建通知器实例，支持项目级别的通知渠道配置
 */
@Slf4j
@Component
public class NotifierFactory {

    /**
     * 创建单个通知器
     *
     * @param config 通知渠道配置
     * @return 通知器实例
     * @throws IllegalArgumentException 不支持的渠道类型
     */
    public Notifier createNotifier(NotificationChannelConfig config) {
        if (config == null || config.getType() == null) {
            throw new IllegalArgumentException("通知渠道配置无效");
        }

        String type = config.getType().toLowerCase();
        log.debug("创建通知器: type={}, webhookUrl={}", type, maskWebhookUrl(config.getWebhookUrl()));

        return switch (type) {
            case "dingtalk" -> new DingTalkNotifier(
                    config.getWebhookUrl(),
                    Boolean.TRUE.equals(config.getSecretEnabled()),
                    config.getSecret()
            );
            case "wecom" -> new WeComNotifier(config.getWebhookUrl());
            case "feishu" -> new FeishuNotifier(config.getWebhookUrl());
            case "webhook" -> new WebhookNotifier(config.getWebhookUrl());
            default -> throw new IllegalArgumentException("不支持的通知渠道类型: " + type);
        };
    }

    /**
     * 批量创建通知器
     *
     * @param configs 通知渠道配置列表
     * @return 通知器实例列表
     */
    public List<Notifier> createNotifiers(List<NotificationChannelConfig> configs) {
        if (configs == null || configs.isEmpty()) {
            return Collections.emptyList();
        }

        return configs.stream()
                .filter(this::isValidConfig)
                .map(this::createNotifier)
                .collect(Collectors.toList());
    }

    /**
     * 验证配置是否有效
     */
    private boolean isValidConfig(NotificationChannelConfig config) {
        if (config == null || config.getType() == null) {
            return false;
        }
        // enabled 为 false 时跳过
        if (Boolean.FALSE.equals(config.getEnabled())) {
            return false;
        }
        // webhookUrl 是必须的
        return config.getWebhookUrl() != null && !config.getWebhookUrl().isBlank();
    }

    /**
     * 遮蔽 Webhook URL 中的敏感信息（用于日志）
     */
    private String maskWebhookUrl(String url) {
        if (url == null || url.length() < 20) {
            return url;
        }
        return url.substring(0, 20) + "***";
    }
}
