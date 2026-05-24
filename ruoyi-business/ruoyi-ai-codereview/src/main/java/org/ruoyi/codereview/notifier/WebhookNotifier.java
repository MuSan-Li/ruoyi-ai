package org.ruoyi.codereview.notifier;

import cn.hutool.core.io.IoUtil;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
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

            String jsonStr = JSONUtil.toJsonStr(requestBody);

            // 使用 HttpURLConnection 确保正确的 UTF-8 编码
            URL url = new URL(webhookUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setDoOutput(true);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(30000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonStr.getBytes(StandardCharsets.UTF_8));
            }

            String response = IoUtil.read(conn.getInputStream(), StandardCharsets.UTF_8);
            log.info("Webhook 通知响应: {}", response);
            return true;
        } catch (Exception e) {
            log.error("发送 Webhook 通知失败", e);
            return false;
        }
    }
}