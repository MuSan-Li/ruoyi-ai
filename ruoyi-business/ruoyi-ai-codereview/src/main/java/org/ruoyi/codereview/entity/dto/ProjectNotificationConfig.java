package org.ruoyi.codereview.entity.dto;

import cn.hutool.json.JSONUtil;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 项目通知配置 DTO
 * <p>
 * 对应 cr_project_config.notification_channels 字段的 JSON 结构，
 * 示例：
 * <pre>
 * {
 *   "channels": [
 *     {"type": "dingtalk", "webhookUrl": "https://oapi.dingtalk.com/..."},
 *     {"type": "wecom", "webhookUrl": "https://qyapi.weixin.qq.com/..."},
 *     {"type": "feishu", "webhookUrl": "https://open.feishu.cn/..."}
 *   ]
 * }
 * </pre>
 */
@Data
public class ProjectNotificationConfig {

    /**
     * 通知渠道列表
     */
    private List<NotificationChannelConfig> channels = new ArrayList<>();

    /**
     * 从 JSON 字符串解析为配置对象
     * <p>
     * 支持两种格式：
     * 1. 数组格式: [{"type":"wecom","webhookUrl":"..."}]
     * 2. 对象格式: {"channels":[{"type":"wecom","webhookUrl":"..."}]}
     *
     * @param json JSON 字符串
     * @return 配置对象，解析失败时返回空对象
     */
    public static ProjectNotificationConfig fromJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new ProjectNotificationConfig();
        }
        try {
            String trimmed = json.trim();
            // 支持直接数组格式: [{...}]
            if (trimmed.startsWith("[")) {
                ProjectNotificationConfig config = new ProjectNotificationConfig();
                List<NotificationChannelConfig> channelList = JSONUtil.toList(trimmed, NotificationChannelConfig.class);
                config.setChannels(channelList);
                return config;
            }
            // 对象格式: {"channels": [...]}
            return JSONUtil.toBean(json, ProjectNotificationConfig.class);
        } catch (Exception e) {
            // 解析失败返回空对象
            return new ProjectNotificationConfig();
        }
    }

    /**
     * 是否有有效的渠道配置
     */
    public boolean hasChannels() {
        return channels != null && !channels.isEmpty();
    }
}
