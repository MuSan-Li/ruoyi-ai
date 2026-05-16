package org.ruoyi.codereview.notifier;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * 自定义 Webhook 通知器
 */
@Slf4j
public class WebhookNotifier implements Notifier {

    private final String webhookUrl;

    public WebhookNotifier(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    @Override
    public boolean send(String title, String content) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("title", title);
            requestBody.put("content", content);
            requestBody.put("timestamp", System.currentTimeMillis());

            String response = HttpUtil.createPost(webhookUrl)
                    .header("Content-Type", "application/json")
                    .body(JSONUtil.toJsonStr(requestBody))
                    .timeout(30000)
                    .execute()
                    .body();

            log.info("Webhook 通知响应: {}", response);
            return true;
        } catch (Exception e) {
            log.error("发送 Webhook 通知失败", e);
            return false;
        }
    }
}