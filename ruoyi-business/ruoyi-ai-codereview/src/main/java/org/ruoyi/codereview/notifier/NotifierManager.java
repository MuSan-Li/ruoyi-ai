package org.ruoyi.codereview.notifier;

import lombok.extern.slf4j.Slf4j;
import org.ruoyi.codereview.entity.ProjectConfig;
import org.ruoyi.codereview.entity.dto.ProjectNotificationConfig;
import org.ruoyi.codereview.service.ReviewConfigService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 通知管理器 - 统一管理所有通知器
 * <p>
 * 支持项目级别的通知渠道配置：
 * 1. 优先使用事件中携带的通知配置
 * 2. 其次使用项目配置中的通知渠道
 * 3. 最后 fallback 到全局默认通知器
 */
@Slf4j
@Component
public class NotifierManager {

    private final ReviewConfigService configService;
    private final NotifierFactory notifierFactory;

    /**
     * 全局默认通知器（fallback）
     */
    private final List<Notifier> defaultNotifiers;

    public NotifierManager(List<Notifier> notifiers,
                           ReviewConfigService configService,
                           NotifierFactory notifierFactory) {
        this.defaultNotifiers = new ArrayList<>(notifiers);
        this.configService = configService;
        this.notifierFactory = notifierFactory;
        log.info("NotifierManager 初始化完成，全局默认通知器数量: {}", defaultNotifiers.size());
    }

    /**
     * 发送 MR 审查完成通知
     */
    public void notifyMergeRequestReviewed(String projectName, String platform,
                                           String author, String sourceBranch, String targetBranch,
                                           String url, int score, String reviewResult) {
        notifyMergeRequestReviewed(projectName, platform, author, sourceBranch, targetBranch,
            url, score, reviewResult, null);
    }

    /**
     * 发送 MR 审查完成通知（带通知配置）
     */
    public void notifyMergeRequestReviewed(String projectName, String platform,
                                           String author, String sourceBranch, String targetBranch,
                                           String url, int score, String reviewResult,
                                           String notificationChannels) {
        List<Notifier> notifiers = getNotifiers(notificationChannels, projectName, platform);
        String title = "代码审查完成 - " + projectName;
        String content = buildMergeRequestContent(projectName, author, sourceBranch, targetBranch, url, score, reviewResult);

        sendToNotifiers(notifiers, title, content, projectName);
    }

    /**
     * 发送 Push 审查完成通知
     */
    public void notifyPushReviewed(String projectName, String platform,
                                   String author, String branch, int score, String reviewResult) {
        notifyPushReviewed(projectName, platform, author, branch, score, reviewResult, null);
    }

    /**
     * 发送 Push 审查完成通知（带通知配置）
     */
    public void notifyPushReviewed(String projectName, String platform,
                                   String author, String branch, int score, String reviewResult,
                                   String notificationChannels) {
        List<Notifier> notifiers = getNotifiers(notificationChannels, projectName, platform);
        String title = "代码审查完成 - " + projectName;
        String content = buildPushContent(projectName, author, branch, score, reviewResult);

        sendToNotifiers(notifiers, title, content, projectName);
    }

    /**
     * 发送审查失败通知
     */
    public void notifyReviewFailed(String projectName, String platform, String eventType, String errorMessage) {
        List<Notifier> notifiers = getNotifiers(null, projectName, platform);
        String title = "代码审查失败 - " + projectName;
        String content = buildFailureContent(projectName, platform, eventType, errorMessage);

        sendToNotifiers(notifiers, title, content, projectName);
    }

    /**
     * 发送日报通知
     */
    public void notifyDailyReport(String report) {
        String title = "代码审查日报";
        sendToNotifiers(defaultNotifiers, title, report, "daily-report");
    }

    /**
     * 测试通知配置
     */
    public void testNotification(ProjectConfig config) {
        if (config == null || config.getNotificationChannels() == null) {
            log.warn("测试通知失败：配置为空");
            return;
        }

        ProjectNotificationConfig notificationConfig = ProjectNotificationConfig.fromJson(config.getNotificationChannels());
        List<Notifier> notifiers = notifierFactory.createNotifiers(notificationConfig.getChannels());

        String title = "测试通知 - " + config.getProjectName();
        String content = "这是一条测试通知，用于验证通知渠道配置是否正确。\n\n项目: " + config.getProjectName() + "\n平台: " + config.getPlatform();

        sendToNotifiers(notifiers, title, content, config.getProjectName());
    }

    // ==================== 核心方法 ====================

    /**
     * 获取通知器列表
     * <p>
     * 优先级：事件配置 > 项目配置 > 全局默认
     */
    private List<Notifier> getNotifiers(String channelsConfig, String projectName, String platform) {
        // 1. 优先使用事件中的通知配置
        if (channelsConfig != null && !channelsConfig.isEmpty()) {
            ProjectNotificationConfig config = ProjectNotificationConfig.fromJson(channelsConfig);
            if (config.hasChannels()) {
                List<Notifier> notifiers = notifierFactory.createNotifiers(config.getChannels());
                if (!notifiers.isEmpty()) {
                    log.debug("使用事件通知配置，渠道数量: {}", notifiers.size());
                    return notifiers;
                }
            }
        }

        // 2. 使用项目配置中的通知渠道
        ProjectConfig projectConfig = configService.getProjectConfig(projectName, platform);
        if (projectConfig != null && projectConfig.getNotificationChannels() != null
            && !projectConfig.getNotificationChannels().isEmpty()) {
            ProjectNotificationConfig config = ProjectNotificationConfig.fromJson(projectConfig.getNotificationChannels());
            if (config.hasChannels()) {
                List<Notifier> notifiers = notifierFactory.createNotifiers(config.getChannels());
                if (!notifiers.isEmpty()) {
                    log.debug("使用项目通知配置，渠道数量: {}", notifiers.size());
                    return notifiers;
                }
            }
        }

        // 3. fallback 到全局默认通知器
        log.debug("使用全局默认通知器，数量: {}", defaultNotifiers.size());
        return defaultNotifiers;
    }

    /**
     * 发送通知到指定通知器列表
     */
    private void sendToNotifiers(List<Notifier> notifiers, String title, String content, String projectName) {
        if (notifiers == null || notifiers.isEmpty()) {
            log.warn("无可用的通知器，跳过通知发送: project={}", projectName);
            return;
        }

        log.info("发送通知: project={}, title={}, 渠道数量={}", projectName, title, notifiers.size());

        for (Notifier notifier : notifiers) {
            try {
                boolean success = notifier.send(title, content);
                log.info("通知发送{}: {}", success ? "成功" : "失败", notifier.getClass().getSimpleName());
            } catch (Exception e) {
                log.error("通知发送异常: {}", notifier.getClass().getSimpleName(), e);
            }
        }
    }

    // ==================== 内容构建 ====================

    private String buildMergeRequestContent(String projectName, String author,
                                            String sourceBranch, String targetBranch,
                                            String url, int score, String reviewResult) {
        StringBuilder sb = new StringBuilder();

        // 基本信息卡片
        sb.append("### ").append(projectName).append("\n\n");
        sb.append("**作者**: ").append(author).append("\n");
        sb.append("**分支**: ").append(sourceBranch).append(" -> ").append(targetBranch).append("\n");
        sb.append("**评分**: ").append(getScoreDisplay(score)).append("\n");
        sb.append("**链接**: [点击查看](").append(url).append(")\n\n");

        // 提取关键问题摘要
        sb.append("---\n\n");
        sb.append("### 审查摘要\n\n");
        sb.append(extractSummary(reviewResult));

        return sb.toString();
    }

    private String buildPushContent(String projectName, String author, String branch, int score, String reviewResult) {
        StringBuilder sb = new StringBuilder();

        sb.append("### ").append(projectName).append("\n\n");
        sb.append("**作者**: ").append(author).append("\n");
        sb.append("**分支**: ").append(branch).append("\n");
        sb.append("**评分**: ").append(getScoreDisplay(score)).append("\n\n");

        sb.append("---\n\n");
        sb.append("### 审查摘要\n\n");
        sb.append(extractSummary(reviewResult));

        return sb.toString();
    }

    private String buildFailureContent(String projectName, String platform, String eventType, String errorMessage) {
        StringBuilder sb = new StringBuilder();
        sb.append("### ").append(projectName).append("\n\n");
        sb.append("**平台**: ").append(platform).append("\n");
        sb.append("**事件**: ").append(eventType).append("\n\n");
        sb.append("---\n\n");
        sb.append("**错误**: ").append(truncate(errorMessage, 200));

        return sb.toString();
    }

    /**
     * 获取评分显示（带颜色标识）
     */
    private String getScoreDisplay(int score) {
        String level;
        if (score >= 90) {
            level = "优秀";
        } else if (score >= 80) {
            level = "良好";
        } else if (score >= 70) {
            level = "合格";
        } else if (score >= 60) {
            level = "待改进";
        } else {
            level = "不通过";
        }
        return score + "/100 (" + level + ")";
    }

    /**
     * 从审查结果中提取摘要
     */
    private String extractSummary(String reviewResult) {
        if (reviewResult == null || reviewResult.isEmpty()) {
            return "无审查结果";
        }

        StringBuilder summary = new StringBuilder();

        // 提取各维度得分
        Pattern scorePattern = Pattern.compile("###\\s*\\d+\\.\\s*([^（]+)[（(]([^）)]+)[）)]");
        Matcher matcher = scorePattern.matcher(reviewResult);

        boolean foundScores = false;
        while (matcher.find() && !foundScores) {
            String dimension = matcher.group(1).trim();
            String scores = matcher.group(2).trim();
            summary.append("- ").append(dimension).append(": ").append(scores).append("\n");
            foundScores = true;
        }

        // 继续提取其他维度
        while (matcher.find()) {
            String dimension = matcher.group(1).trim();
            String scores = matcher.group(2).trim();
            summary.append("- ").append(dimension).append(": ").append(scores).append("\n");
        }

        // 提取总分
        Pattern totalPattern = Pattern.compile("\\*\\*总分[：:]\\s*(\\d+)分?\\*\\*");
        Matcher totalMatcher = totalPattern.matcher(reviewResult);
        if (totalMatcher.find()) {
            summary.append("\n**总分: ").append(totalMatcher.group(1)).append("分**\n");
        }

        // 如果没有提取到结构化信息，返回截断的原文
        if (summary.length() == 0) {
            return truncate(reviewResult, 500);
        }

        // 提取主要问题（查找"问题"或"建议"相关内容）
        summary.append("\n**主要问题**:\n");
        Pattern issuePattern = Pattern.compile("(?:问题|建议|修改建议)[:：]?\\s*([^\\n]+)");
        Matcher issueMatcher = issuePattern.matcher(reviewResult);
        int issueCount = 0;
        while (issueMatcher.find() && issueCount < 3) {
            String issue = issueMatcher.group(1).trim();
            if (!issue.isEmpty() && issue.length() > 5) {
                summary.append("- ").append(truncate(issue, 100)).append("\n");
                issueCount++;
            }
        }

        return summary.toString();
    }

    /**
     * 截断字符串
     */
    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }
}
