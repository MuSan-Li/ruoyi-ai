package org.ruoyi.codereview.notifier;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/**
 * 钉钉通知器
 */
@Slf4j
public class DingTalkNotifier implements Notifier {

    private final String webhookUrl;
    private final boolean secretEnabled;
    private final String secret;

    public DingTalkNotifier(String webhookUrl, boolean secretEnabled, String secret) {
        this.webhookUrl = webhookUrl;
        this.secretEnabled = secretEnabled;
        this.secret = secret;
    }

    @Override
    public boolean send(String title, String content) {
        try {
            String url = webhookUrl;

            if (secretEnabled && StrUtil.isNotBlank(secret)) {
                url = addSign(url, secret);
            }

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("msgtype", "markdown");

            Map<String, Object> markdown = new HashMap<>();
            markdown.put("title", title);
            markdown.put("text", content);
            requestBody.put("markdown", markdown);

            String response = HttpUtil.createPost(url)
                    .header("Content-Type", "application/json")
                    .body(JSONUtil.toJsonStr(requestBody))
                    .timeout(30000)
                    .execute()
                    .body();

            JSONObject result = JSONUtil.parseObj(response);
            return "0".equals(result.getStr("errcode"));
        } catch (Exception e) {
            log.error("发送钉钉通知失败", e);
            return false;
        }
    }

    private String addSign(String webhookUrl, String secret) {
        try {
            long timestamp = System.currentTimeMillis();
            String stringToSign = timestamp + "\n" + secret;
            MessageDigest md = MessageDigest.getInstance("HmacSHA256");
            byte[] hash = md.digest(stringToSign.getBytes(StandardCharsets.UTF_8));
            String sign = java.util.Base64.getEncoder().encodeToString(hash);

            return webhookUrl + "&timestamp=" + timestamp + "&sign=" + sign;
        } catch (Exception e) {
            log.error("钉钉签名失败", e);
            return webhookUrl;
        }
    }
}