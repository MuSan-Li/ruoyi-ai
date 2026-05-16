package org.ruoyi.codereview.notifier;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;

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

            log.info("发送企业微信通知: url={}", webhookUrl);
            String response = HttpUtil.createPost(webhookUrl)
                    .header("Content-Type", "application/json")
                    .body(JSONUtil.toJsonStr(requestBody))
                    .timeout(30000)
                    .execute()
                    .body();

            JSONObject result = JSONUtil.parseObj(response);
            return "0".equals(result.getStr("errcode"));
        } catch (Exception e) {
            log.error("发送企业微信通知失败", e);
            return false;
        }
    }
}