package org.ruoyi.codereview.notifier;

import cn.hutool.core.io.IoUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 飞书通知器
 */
@Slf4j
public class FeishuNotifier implements Notifier {

    private final String webhookUrl;

    public FeishuNotifier(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    @Override
    public boolean send(String title, String content) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("msg_type", "interactive");

            Map<String, Object> card = new HashMap<>();
            card.put("header", createHeader(title));
            card.put("elements", createElements(content));

            requestBody.put("card", card);

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

            JSONObject result = JSONUtil.parseObj(response);
            return "0".equals(result.getStr("code")) || "success".equalsIgnoreCase(result.getStr("msg"));
        } catch (Exception e) {
            log.error("发送飞书通知失败", e);
            return false;
        }
    }

    private Map<String, Object> createHeader(String title) {
        Map<String, Object> header = new HashMap<>();
        header.put("title", createTag(title));
        header.put("template", "blue");
        return header;
    }

    private List<Map<String, Object>> createElements(String content) {
        List<Map<String, Object>> elements = new ArrayList<>();
        Map<String, Object> element = new HashMap<>();
        element.put("tag", "div");
        element.put("text", createText(content));
        elements.add(element);
        return elements;
    }

    private Map<String, Object> createTag(String title) {
        Map<String, Object> tag = new HashMap<>();
        tag.put("tag", "plain_text");
        tag.put("content", title);
        tag.put("color", "white");
        return tag;
    }

    private Map<String, Object> createText(String content) {
        Map<String, Object> text = new HashMap<>();
        text.put("tag", "lark_md");
        text.put("content", content);
        return text;
    }
}