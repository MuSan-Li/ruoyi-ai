package org.ruoyi.codereview.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.codereview.entity.ProjectConfig;
import org.ruoyi.codereview.entity.ReviewConfig;
import org.ruoyi.codereview.entity.ReviewRule;
import org.ruoyi.codereview.entity.dto.PlatformConfig;
import org.ruoyi.codereview.mapper.ProjectConfigMapper;
import org.ruoyi.codereview.mapper.ReviewConfigMapper;
import org.ruoyi.codereview.mapper.ReviewRuleMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 代码审查配置服务
 * 从数据库加载配置，支持缓存和热更新
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewConfigService {

    private final ReviewConfigMapper reviewConfigMapper;
    private final ReviewRuleMapper reviewRuleMapper;
    private final ProjectConfigMapper projectConfigMapper;

    /** 配置缓存: key -> value */
    private final Map<String, String> configCache = new ConcurrentHashMap<>();

    /** 规则缓存 */
    private volatile List<ReviewRule> ruleCache;

    /** 项目配置缓存: projectName:platform -> config */
    private final Map<String, ProjectConfig> projectConfigCache = new ConcurrentHashMap<>();

    /** 缓存是否已初始化 */
    private volatile boolean initialized = false;

    // ==================== 配置读取 ====================

    /**
     * 获取字符串配置
     */
    public String getString(String key) {
        ensureInitialized();
        return configCache.get(key);
    }

    /**
     * 获取字符串配置（带默认值）
     */
    public String getString(String key, String defaultValue) {
        String value = getString(key);
        return value != null ? value : defaultValue;
    }

    /**
     * 获取整数配置
     */
    public int getInt(String key, int defaultValue) {
        String value = getString(key);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("配置项 {} 的值 {} 不是有效整数，使用默认值 {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    /**
     * 获取布尔配置
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        String value = getString(key);
        if (value == null) return defaultValue;
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    /**
     * 获取 BigDecimal 配置
     */
    public BigDecimal getBigDecimal(String key, BigDecimal defaultValue) {
        String value = getString(key);
        if (value == null) return defaultValue;
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            log.warn("配置项 {} 的值 {} 不是有效数值，使用默认值 {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    // ==================== 规则读取 ====================

    /**
     * 获取所有启用的审查规则
     */
    public List<ReviewRule> getEnabledRules() {
        ensureInitialized();
        return ruleCache;
    }

    /**
     * 获取指定维度的审查规则
     */
    public List<ReviewRule> getRulesByDimension(String dimension) {
        return getEnabledRules().stream()
            .filter(r -> dimension.equals(r.getDimension()))
            .toList();
    }

    // ==================== 项目配置读取 ====================

    /**
     * 获取项目配置
     */
    public ProjectConfig getProjectConfig(String projectName, String platform) {
        ensureInitialized();
        String key = projectName + ":" + platform;
        ProjectConfig config = projectConfigCache.get(key);
        if (config != null) return config;

        // 查找通用项目配置
        key = projectName + ":*";
        return projectConfigCache.get(key);
    }

    /**
     * 获取平台配置（从项目配置中提取）
     *
     * @param projectName 项目名称
     * @param platform    平台类型
     * @return 平台配置，如果项目未配置则返回 null
     */
    public PlatformConfig getPlatformConfig(String projectName, String platform) {
        ProjectConfig projectConfig = getProjectConfig(projectName, platform);
        if (projectConfig == null) {
            return null;
        }

        String url = projectConfig.getPlatformUrl();
        String token = projectConfig.getPlatformToken();
        String webhookSecret = projectConfig.getWebhookSecret();

        if (url == null || url.isEmpty() || token == null || token.isEmpty()) {
            return null;
        }

        return PlatformConfig.fromProjectConfig(platform, url, token, webhookSecret);
    }

    /**
     * 获取 Webhook 密钥（用于验证 Webhook 请求）
     *
     * @param projectName 项目名称
     * @param platform    平台类型
     * @return Webhook 密钥
     */
    public String getWebhookSecret(String projectName, String platform) {
        ProjectConfig projectConfig = getProjectConfig(projectName, platform);
        if (projectConfig != null && projectConfig.getWebhookSecret() != null) {
            return projectConfig.getWebhookSecret();
        }
        // fallback 到全局配置
        return getString("platform." + platform + ".webhook_secret");
    }

    // ==================== 缓存管理 ====================

    /**
     * 确保缓存已初始化
     */
    private void ensureInitialized() {
        if (!initialized) {
            refreshCache();
        }
    }

    /**
     * 刷新所有缓存
     * <p>
     * 使用 Copy-On-Write 模式：先构建新缓存，再原子替换，避免刷新期间缓存为空
     */
    public synchronized void refreshCache() {
        log.info("刷新代码审查配置缓存...");
        try {
            // 先构建新缓存（不修改现有缓存）
            Map<String, String> newConfigCache = buildConfigCache();
            List<ReviewRule> newRuleCache = buildRuleCache();
            Map<String, ProjectConfig> newProjectConfigCache = buildProjectConfigCache();

            // 原子替换：先清空再填充（ConcurrentHashMap 保证线程安全）
            configCache.clear();
            configCache.putAll(newConfigCache);

            ruleCache = newRuleCache;

            projectConfigCache.clear();
            projectConfigCache.putAll(newProjectConfigCache);

            initialized = true;
            log.info("代码审查配置缓存刷新完成: 配置{}项, 规则{}条, 项目{}个",
                configCache.size(),
                ruleCache != null ? ruleCache.size() : 0,
                projectConfigCache.size());
        } catch (Exception e) {
            log.error("刷新代码审查配置缓存失败", e);
        }
    }

    /**
     * 构建配置缓存（不修改现有缓存）
     */
    private Map<String, String> buildConfigCache() {
        Map<String, String> cache = new ConcurrentHashMap<>();
        List<ReviewConfig> configs = reviewConfigMapper.selectList(
            new LambdaQueryWrapper<ReviewConfig>()
                .eq(ReviewConfig::getStatus, 1)
        );
        for (ReviewConfig config : configs) {
            cache.put(config.getConfigKey(), config.getConfigValue());
        }
        return cache;
    }

    /**
     * 构建规则缓存
     */
    private List<ReviewRule> buildRuleCache() {
        return reviewRuleMapper.selectList(
            new LambdaQueryWrapper<ReviewRule>()
                .eq(ReviewRule::getEnabled, 1)
                .orderByAsc(ReviewRule::getSortOrder)
        );
    }

    /**
     * 构建项目配置缓存（不修改现有缓存）
     */
    private Map<String, ProjectConfig> buildProjectConfigCache() {
        Map<String, ProjectConfig> cache = new ConcurrentHashMap<>();
        List<ProjectConfig> configs = projectConfigMapper.selectList(null);
        for (ProjectConfig config : configs) {
            String key = config.getProjectName() + ":" + config.getPlatform();
            cache.put(key, config);
        }
        return cache;
    }

    /**
     * 定时刷新缓存（每5分钟）
     */
    @Scheduled(fixedRate = 300000)
    public void scheduledRefresh() {
        if (initialized) {
            refreshCache();
        }
    }

    // ==================== 配置更新 ====================

    /**
     * 更新配置
     */
    public void updateConfig(String key, String value) {
        ReviewConfig config = reviewConfigMapper.selectOne(
            new LambdaQueryWrapper<ReviewConfig>()
                .eq(ReviewConfig::getConfigKey, key)
        );
        if (config != null) {
            config.setConfigValue(value);
            reviewConfigMapper.updateById(config);
        } else {
            config = new ReviewConfig();
            config.setConfigKey(key);
            config.setConfigValue(value);
            config.setConfigType("STRING");
            config.setCategory("general");
            config.setStatus(1);
            reviewConfigMapper.insert(config);
        }
        configCache.put(key, value);
        log.info("配置更新: {} = {}", key, value);
    }

    // ==================== 评分辅助 ====================

    /**
     * 获取评分等级
     */
    public String getScoreLevel(int score) {
        if (score >= getInt("scoring.excellent_score", 90)) return "优秀";
        if (score >= getInt("scoring.good_score", 80)) return "良好";
        if (score >= getInt("scoring.pass_score", 70)) return "合格";
        if (score >= getInt("scoring.pass_score", 60)) return "待改进";
        return "不通过";
    }

    /**
     * 是否通过审查
     */
    public boolean isPassed(int score) {
        return score >= getInt("scoring.pass_score", 60);
    }

    /**
     * 获取维度权重
     */
    public int getDimensionWeight(String dimension) {
        return switch (dimension) {
            case "functionality" -> getInt("scoring.functionality.weight", 40);
            case "security" -> getInt("scoring.security.weight", 30);
            case "best_practice" -> getInt("scoring.best_practice.weight", 20);
            case "performance" -> getInt("scoring.performance.weight", 5);
            case "commit_message" -> getInt("scoring.commit_message.weight", 5);
            default -> 0;
        };
    }

    /**
     * 计算加权总分
     */
    public BigDecimal calculateWeightedScore(Map<String, BigDecimal> dimensionScores) {
        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal weightedSum = BigDecimal.ZERO;

        for (Map.Entry<String, BigDecimal> entry : dimensionScores.entrySet()) {
            int weight = getDimensionWeight(entry.getKey());
            if (weight > 0) {
                totalWeight = totalWeight.add(BigDecimal.valueOf(weight));
                // 将百分比分数转为该维度满分比例
                BigDecimal dimensionScore = entry.getValue()
                    .multiply(BigDecimal.valueOf(weight))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                weightedSum = weightedSum.add(dimensionScore);
            }
        }

        if (totalWeight.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;

        return weightedSum.multiply(BigDecimal.valueOf(100))
            .divide(totalWeight, 0, RoundingMode.HALF_UP);
    }
}
