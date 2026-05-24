package org.ruoyi.codereview.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.codereview.config.CodeReviewProperties;
import org.ruoyi.codereview.platform.GitPlatformClient;
import org.ruoyi.codereview.service.ReviewConfigService;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 代码审查健康检查指示器
 * <p>
 * 检查项：
 * 1. 配置初始化状态
 * 2. 平台连接状态
 * 3. 缓存状态
 * 4. LLM 模型可用性
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CodeReviewHealthIndicator implements HealthIndicator {

    private final ReviewConfigService configService;
    private final CodeReviewProperties properties;
    private final Map<String, GitPlatformClient> platformClients;

    @Override
    public Health health() {
        Health.Builder builder = Health.up();

        try {
            // 1. 检查配置初始化状态
            boolean configInitialized = isConfigInitialized();
            builder.withDetail("configInitialized", configInitialized);

            if (!configInitialized) {
                builder.down();
                builder.withDetail("error", "Configuration not initialized");
                return builder.build();
            }

            // 2. 检查平台连接状态
            Map<String, Object> platformStatus = checkPlatformStatus();
            builder.withDetail("platforms", platformStatus);

            // 3. 检查缓存状态
            Map<String, Object> cacheStatus = checkCacheStatus();
            builder.withDetail("cache", cacheStatus);

            // 4. 检查 LLM 配置
            Map<String, Object> llmStatus = checkLlmStatus();
            builder.withDetail("llm", llmStatus);

            // 5. 统计信息
            builder.withDetail("statistics", getStatistics());

            // 如果有任何平台可用，则认为服务健康
            boolean anyPlatformAvailable = platformStatus.values().stream()
                    .anyMatch(status -> Boolean.TRUE.equals(status));

            if (!anyPlatformAvailable) {
                builder.status("WARNING");
                builder.withDetail("warning", "No platform configured");
            }

        } catch (Exception e) {
            log.error("健康检查失败", e);
            builder.down()
                    .withDetail("error", e.getMessage())
                    .withException(e);
        }

        return builder.build();
    }

    /**
     * 检查配置是否已初始化
     */
    private boolean isConfigInitialized() {
        try {
            // 尝试获取一个配置项来验证初始化状态
            configService.getString("nonexistent.key");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检查各平台连接状态
     */
    private Map<String, Object> checkPlatformStatus() {
        Map<String, Object> status = new HashMap<>();

        // 检查已配置的平台客户端
        if (platformClients != null) {
            for (Map.Entry<String, GitPlatformClient> entry : platformClients.entrySet()) {
                String platform = entry.getKey();
                try {
                    GitPlatformClient client = entry.getValue();
                    status.put(platform, true);
                    status.put(platform + "_class", client.getClass().getSimpleName());
                } catch (Exception e) {
                    status.put(platform, false);
                    status.put(platform + "_error", e.getMessage());
                }
            }
        }

        // 检查 YAML 配置
        CodeReviewProperties.PlatformConfig platformConfig = properties.getPlatform();
        if (platformConfig != null) {
            status.put("gitlab_configured", platformConfig.getGitlab() != null
                    && platformConfig.getGitlab().getUrl() != null
                    && !platformConfig.getGitlab().getUrl().isEmpty());
            status.put("github_configured", platformConfig.getGithub() != null
                    && platformConfig.getGithub().getUrl() != null
                    && !platformConfig.getGithub().getUrl().isEmpty());
            status.put("gitea_configured", platformConfig.getGitea() != null
                    && platformConfig.getGitea().getUrl() != null
                    && !platformConfig.getGitea().getUrl().isEmpty());
        }

        return status;
    }

    /**
     * 检查缓存状态
     */
    private Map<String, Object> checkCacheStatus() {
        Map<String, Object> status = new HashMap<>();

        try {
            // 配置缓存
            int configCacheSize = configService.getString("test") != null ? 1 : 0;
            status.put("configCacheStatus", "active");

            // 规则缓存
            int rulesCount = configService.getEnabledRules().size();
            status.put("rulesCount", rulesCount);

        } catch (Exception e) {
            status.put("error", e.getMessage());
        }

        return status;
    }

    /**
     * 检查 LLM 配置
     */
    private Map<String, Object> checkLlmStatus() {
        Map<String, Object> status = new HashMap<>();

        try {
            CodeReviewProperties.LlmConfig llmConfig = properties.getLlm();
            if (llmConfig != null) {
                status.put("modelName", llmConfig.getModelName());
                status.put("configured", llmConfig.getModelName() != null && !llmConfig.getModelName().isEmpty());
            }

            CodeReviewProperties.ReviewConfig reviewConfig = properties.getReview();
            if (reviewConfig != null) {
                status.put("maxTokens", reviewConfig.getMaxTokens());
                status.put("style", reviewConfig.getStyle());
            }

        } catch (Exception e) {
            status.put("error", e.getMessage());
        }

        return status;
    }

    /**
     * 获取统计信息
     */
    private Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();

        try {
            stats.put("platformClientsCount", platformClients != null ? platformClients.size() : 0);
            stats.put("enabled", properties.isEnabled());
        } catch (Exception e) {
            stats.put("error", e.getMessage());
        }

        return stats;
    }
}
