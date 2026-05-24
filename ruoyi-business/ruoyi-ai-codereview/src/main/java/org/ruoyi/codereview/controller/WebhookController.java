package org.ruoyi.codereview.controller;

import cn.hutool.json.JSONException;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.codereview.config.CodeReviewProperties;
import org.ruoyi.codereview.service.CodeReviewService;
import org.ruoyi.common.core.domain.R;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Webhook 控制器
 * <p>
 * 安全特性：
 * - 使用恒定时间比较防止时序攻击
 * - GitHub 使用 HMAC-SHA256 签名验证
 * - 请求体大小限制（建议在 application.yml 配置 spring.servlet.multipart.max-file-size）
 * <p>
 * 速率限制建议：
 * - 可使用 Resilience4j 或自定义拦截器实现
 * - 建议配置：每 IP 每分钟最多 60 次请求
 */
@Slf4j
@RestController
@RequestMapping("/codereview/webhook")
@RequiredArgsConstructor
public class WebhookController {

    private final CodeReviewService codeReviewService;
    private final CodeReviewProperties properties;

    /**
     * 最大请求体大小：1MB
     */
    private static final int MAX_PAYLOAD_SIZE = 1024 * 1024;

    @PostMapping("/gitlab")
    public R<Void> handleGitLabWebhook(
        @RequestHeader(value = "X-Gitlab-Token", required = false) String token,
        @RequestHeader(value = "X-Gitlab-Event", required = false) String event,
        @RequestBody String payload) {

        log.info("收到 GitLab Webhook, 事件类型: {}", event);

        // 请求体大小检查
        if (payload != null && payload.length() > MAX_PAYLOAD_SIZE) {
            log.warn("GitLab Webhook 请求体过大: {} bytes", payload.length());
            return R.fail("Payload too large");
        }

        if (!properties.getPlatform().getGitlab().isEnabled()) {
            return R.fail("GitLab integration disabled");
        }

        String webhookSecret = properties.getPlatform().getGitlab().getWebhookSecret();
        if (webhookSecret != null && !webhookSecret.isEmpty()) {
            if (!constantTimeEquals(webhookSecret, token)) {
                log.warn("GitLab Webhook Token 验证失败");
                return R.fail("Unauthorized");
            }
        }

        try {
            // JSON 结构验证
            JSONObject json = parseAndValidate(payload, "gitlab");
            validateGitLabPayload(json, event);

            if (json.containsKey("object_kind")) {
                String objectKind = json.getStr("object_kind");
                if ("merge_request".equals(objectKind) || "Merge Request".equals(event)) {
                    codeReviewService.handleGitLabMergeRequest(payload);
                } else if ("push".equals(objectKind) || "Push Hook".equals(event)) {
                    codeReviewService.handleGitLabPush(payload);
                } else {
                    log.info("GitLab 忽略不处理的事件类型: object_kind={}, event={}", objectKind, event);
                }
            } else {
                log.warn("GitLab Webhook payload 缺少 object_kind 字段");
            }
            return R.ok();
        } catch (IllegalArgumentException e) {
            log.warn("GitLab Webhook 验证失败: {}", e.getMessage());
            return R.fail("Validation failed: " + e.getMessage());
        } catch (Exception e) {
            log.error("处理 GitLab Webhook 失败", e);
            return R.fail(e.getMessage());
        }
    }

    @PostMapping("/github")
    public R<Void> handleGitHubWebhook(
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestHeader(value = "X-GitHub-Event", required = false) String event,
            @RequestBody String payload) {

        log.info("收到 GitHub Webhook, 事件类型: {}", event);

        // 请求体大小检查
        if (payload != null && payload.length() > MAX_PAYLOAD_SIZE) {
            log.warn("GitHub Webhook 请求体过大: {} bytes", payload.length());
            return R.fail("Payload too large");
        }

        if (!properties.getPlatform().getGithub().isEnabled()) {
            return R.fail("GitHub integration disabled");
        }

        // GitHub 签名验证
        String webhookSecret = properties.getPlatform().getGithub().getWebhookSecret();
        if (webhookSecret != null && !webhookSecret.isEmpty()) {
            if (!verifyGitHubSignature(signature, payload, webhookSecret)) {
                log.warn("GitHub Webhook 签名验证失败");
                return R.fail("Unauthorized - Invalid signature");
            }
        }

        try {
            // JSON 结构验证
            JSONObject json = parseAndValidate(payload, "github");
            validateGitHubPayload(json, event);

            if ("pull_request".equals(event)) {
                codeReviewService.handleGitHubPullRequest(payload);
            } else if ("push".equals(event)) {
                codeReviewService.handleGitHubPush(payload);
            }
            return R.ok();
        } catch (IllegalArgumentException e) {
            log.warn("GitHub Webhook 验证失败: {}", e.getMessage());
            return R.fail("Validation failed: " + e.getMessage());
        } catch (Exception e) {
            log.error("处理 GitHub Webhook 失败", e);
            return R.fail(e.getMessage());
        }
    }

    @PostMapping("/gitea")
    public R<Void> handleGiteaWebhook(
        @RequestHeader(value = "X-Gitea-Token", required = false) String token,
        @RequestHeader(value = "X-Gitea-Event", required = false) String event,
        @RequestBody String payload) {

        log.info("收到 Gitea Webhook, 事件类型: {}", event);

        // 请求体大小检查
        if (payload != null && payload.length() > MAX_PAYLOAD_SIZE) {
            log.warn("Gitea Webhook 请求体过大: {} bytes", payload.length());
            return R.fail("Payload too large");
        }

        if (!properties.getPlatform().getGitea().isEnabled()) {
            return R.fail("Gitea integration disabled");
        }

        String webhookSecret = properties.getPlatform().getGitea().getWebhookSecret();
        if (webhookSecret != null && !webhookSecret.isEmpty()) {
            if (!constantTimeEquals(webhookSecret, token)) {
                log.warn("Gitea Webhook Token 验证失败");
                return R.fail("Unauthorized");
            }
        }

        try {
            // JSON 结构验证
            JSONObject json = parseAndValidate(payload, "gitea");
            validateGiteaPayload(json, event);

            if ("pull_request".equals(event)) {
                codeReviewService.handleGiteaPullRequest(payload);
            } else if ("push".equals(event)) {
                codeReviewService.handleGiteaPush(payload);
            }
            return R.ok();
        } catch (IllegalArgumentException e) {
            log.warn("Gitea Webhook 验证失败: {}", e.getMessage());
            return R.fail("Validation failed: " + e.getMessage());
        } catch (Exception e) {
            log.error("处理 Gitea Webhook 失败", e);
            return R.fail(e.getMessage());
        }
    }

    // ==================== 安全工具方法 ====================

    /**
     * 恒定时间字符串比较
     * <p>
     * 使用 MessageDigest.isEqual() 防止时序攻击，
     * 避免通过响应时间差异推断正确的 Token
     *
     * @param expected 期望值
     * @param actual   实际值
     * @return 是否相等
     */
    private boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = actual.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }

    /**
     * 验证 GitHub Webhook 签名 (HMAC-SHA256)
     *
     * @param signature     签名值，格式为 "sha256=<hex>"
     * @param payload       请求体
     * @param webhookSecret 密钥
     * @return 签名是否有效
     */
    private boolean verifyGitHubSignature(String signature, String payload, String webhookSecret) {
        if (signature == null || signature.isEmpty()) {
            log.warn("GitHub Webhook 缺少签名头");
            return false;
        }

        try {
            // 签名格式: sha256=<hex>
            String expectedSignature = signature.startsWith("sha256=")
                ? signature.substring(7)
                : signature;

            // 计算 HMAC-SHA256
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));

            // 转换为十六进制字符串
            String computedSignature = bytesToHex(hash);

            // 使用恒定时间比较防止时序攻击
            return constantTimeEquals(expectedSignature.toLowerCase(), computedSignature.toLowerCase());
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("GitHub 签名验证算法错误", e);
            return false;
        }
    }

    /**
     * 字节数组转十六进制字符串
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    // ==================== 输入验证方法 ====================

    /**
     * 解析并验证 JSON
     */
    private JSONObject parseAndValidate(String payload, String platform) {
        if (payload == null || payload.isEmpty()) {
            throw new IllegalArgumentException("Empty payload");
        }

        try {
            return JSONUtil.parseObj(payload);
        } catch (JSONException e) {
            log.warn("[{}] 无效的 JSON 格式: {}", platform, e.getMessage());
            throw new IllegalArgumentException("Invalid JSON format");
        }
    }

    /**
     * 验证 GitLab Webhook payload
     */
    private void validateGitLabPayload(JSONObject json, String event) {
        // 验证必要字段
        if (!json.containsKey("project")) {
            throw new IllegalArgumentException("Missing required field: project");
        }

        JSONObject project = json.getJSONObject("project");
        if (project == null || !project.containsKey("id")) {
            throw new IllegalArgumentException("Invalid project field");
        }

        // MR 事件验证
        if (json.containsKey("object_attributes")) {
            JSONObject attrs = json.getJSONObject("object_attributes");
            if (attrs == null) {
                throw new IllegalArgumentException("Invalid object_attributes field");
            }
        }
    }

    /**
     * 验证 GitHub Webhook payload
     */
    private void validateGitHubPayload(JSONObject json, String event) {
        // 验证必要字段
        if (!json.containsKey("repository")) {
            throw new IllegalArgumentException("Missing required field: repository");
        }

        JSONObject repo = json.getJSONObject("repository");
        if (repo == null || !repo.containsKey("id")) {
            throw new IllegalArgumentException("Invalid repository field");
        }

        // PR 事件验证
        if ("pull_request".equals(event)) {
            if (!json.containsKey("pull_request")) {
                throw new IllegalArgumentException("Missing required field: pull_request");
            }
            JSONObject pr = json.getJSONObject("pull_request");
            if (pr == null || !pr.containsKey("number")) {
                throw new IllegalArgumentException("Invalid pull_request field");
            }
        }
    }

    /**
     * 验证 Gitea Webhook payload
     */
    private void validateGiteaPayload(JSONObject json, String event) {
        // Gitea payload 结构与 GitHub 类似
        if (!json.containsKey("repository")) {
            throw new IllegalArgumentException("Missing required field: repository");
        }

        JSONObject repo = json.getJSONObject("repository");
        if (repo == null || !repo.containsKey("id")) {
            throw new IllegalArgumentException("Invalid repository field");
        }
    }
}
