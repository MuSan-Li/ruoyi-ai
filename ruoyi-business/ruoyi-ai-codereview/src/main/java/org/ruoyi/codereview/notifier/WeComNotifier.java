package org.ruoyi.codereview.notifier;

import cn.hutool.core.io.IoUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 企业微信通知器
 */
@Slf4j
public class WeComNotifier implements Notifier {

    private final String webhookUrl;

    public WeComNotifier(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    @Override
    public boolean send(String title, String content) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.error("企业微信 Webhook URL 未配置");
            return false;
        }
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("msgtype", "markdown");

            Map<String, Object> markdown = new HashMap<>();
            markdown.put("content", "### " + title + "\n" + content.replace("\n", "\n> "));
            requestBody.put("markdown", markdown);

            String jsonStr = JSONUtil.toJsonStr(requestBody);
            log.info("发送企业微信通知: url={}, body={}", webhookUrl, jsonStr);

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
            JSONObject result = JSONUtil.parseObj(response);
            return "0".equals(result.getStr("errcode"));
        } catch (Exception e) {
            log.error("发送企业微信通知失败", e);
            return false;
        }
    }
}
