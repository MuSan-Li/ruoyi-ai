package org.ruoyi.codereview.notifier;

import cn.hutool.core.io.IoUtil;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Webhook 通知器抽象基类
 * <p>
 * 提取公共的 HTTP 调用逻辑，子类只需实现：
 * - getNotifierName(): 返回通知器名称
 * - isSuccessResponse(): 判断响应是否成功
 * - getTimeout(): 返回超时时间（可选）
 */
@Slf4j
public abstract class AbstractWebhookNotifier implements Notifier {

    /** 默认超时时间（毫秒） */
    protected static final int DEFAULT_TIMEOUT_MS = 30000;

    @Override
    public boolean send(String title, String content) {
        String url = getWebhookUrl();
        if (url == null || url.isEmpty()) {
            log.warn("[{}] Webhook URL 未配置", getNotifierName());
            return false;
        }

        Map<String, Object> body = buildRequestBody(title, content);
        return sendWebhook(url, body);
    }

    /**
     * 发送 Webhook 请求
     *
     * @param url  Webhook URL
     * @param body 请求体
     * @return 是否发送成功
     */
    protected boolean sendWebhook(String url, Map<String, Object> body) {
        try {
            String jsonStr = JSONUtil.toJsonStr(body);

            // 使用 HttpURLConnection 确保正确的 UTF-8 编码
            URL webhookUrl = new URL(url);
            HttpURLConnection conn = (HttpURLConnection) webhookUrl.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setDoOutput(true);
            conn.setConnectTimeout(getTimeout());
            conn.setReadTimeout(getTimeout());

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonStr.getBytes(StandardCharsets.UTF_8));
            }

            String response = IoUtil.read(conn.getInputStream(), StandardCharsets.UTF_8);

            boolean success = isSuccessResponse(response);
            if (success) {
                log.info("[{}] 通知发送成功", getNotifierName());
            } else {
                log.warn("[{}] 通知发送失败: {}", getNotifierName(), response);
            }
            return success;
        } catch (Exception e) {
            log.error("[{}] 通知发送异常", getNotifierName(), e);
            return false;
        }
    }

    /**
     * 构建请求体
     *
     * @param title   标题
     * @param content 内容
     * @return 请求体 Map
     */
    protected abstract Map<String, Object> buildRequestBody(String title, String content);

    /**
     * 判断响应是否成功
     *
     * @param response HTTP 响应体
     * @return 是否成功
     */
    protected abstract boolean isSuccessResponse(String response);

    /**
     * 获取通知器名称（用于日志）
     *
     * @return 通知器名称
     */
    protected abstract String getNotifierName();

    /**
     * 获取 Webhook URL
     *
     * @return Webhook URL
     */
    protected abstract String getWebhookUrl();

    /**
     * 获取超时时间（毫秒）
     * <p>
     * 子类可覆盖此方法自定义超时时间
     *
     * @return 超时时间
     */
    protected int getTimeout() {
        return DEFAULT_TIMEOUT_MS;
    }
}
