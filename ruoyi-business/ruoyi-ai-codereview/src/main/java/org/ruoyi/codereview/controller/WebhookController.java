package org.ruoyi.codereview.controller;

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
import java.security.NoSuchAlgorithmException;

/**
 * Webhook 控制器
 */
@Slf4j
@RestController
@RequestMapping("/codereview/webhook")
@RequiredArgsConstructor
public class WebhookController {

    private final CodeReviewService codeReviewService;
    private final CodeReviewProperties properties;

    @PostMapping("/gitlab")
    public R<Void> handleGitLabWebhook(
            @RequestHeader(value = "X-Gitlab-Token", required = false) String token,
            @RequestHeader(value = "X-Gitlab-Event", required = false) String event,
            @RequestBody String payload) {

        log.info("收到 GitLab Webhook, 事件类型: {}", event);

        if (!properties.getPlatform().getGitlab().isEnabled()) {
            return R.fail("GitLab integration disabled");
        }

        if (properties.getPlatform().getGitlab().getWebhookSecret() != null
                && !properties.getPlatform().getGitlab().getWebhookSecret().isEmpty()
                && !properties.getPlatform().getGitlab().getWebhookSecret().equals(token)) {
            log.warn("GitLab Webhook Token 验证失败");
            return R.fail("Unauthorized");
        }

        try {
            if (payload.contains("\"object_kind\":\"merge_request\"") || "Merge Request".equals(event)) {
                codeReviewService.handleGitLabMergeRequest(payload);
            } else if (payload.contains("\"object_kind\":\"push\"") || "Push Hook".equals(event)) {
                codeReviewService.handleGitLabPush(payload);
            }
            return R.ok();
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
            if ("pull_request".equals(event)) {
                codeReviewService.handleGitHubPullRequest(payload);
            } else if ("push".equals(event)) {
                codeReviewService.handleGitHubPush(payload);
            }
            return R.ok();
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

        if (!properties.getPlatform().getGitea().isEnabled()) {
            return R.fail("Gitea integration disabled");
        }

        if (properties.getPlatform().getGitea().getWebhookSecret() != null
                && !properties.getPlatform().getGitea().getWebhookSecret().isEmpty()
                && !properties.getPlatform().getGitea().getWebhookSecret().equals(token)) {
            log.warn("Gitea Webhook Token 验证失败");
            return R.fail("Unauthorized");
        }

        try {
            if ("pull_request".equals(event)) {
                codeReviewService.handleGiteaPullRequest(payload);
            } else if ("push".equals(event)) {
                codeReviewService.handleGiteaPush(payload);
            }
            return R.ok();
        } catch (Exception e) {
            log.error("处理 Gitea Webhook 失败", e);
            return R.fail(e.getMessage());
        }
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

            return computedSignature.equalsIgnoreCase(expectedSignature);
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
}
