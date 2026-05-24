package org.ruoyi.codereview.service;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.codereview.config.CodeReviewProperties;
import org.ruoyi.codereview.entity.*;
import org.ruoyi.codereview.entity.dto.PlatformConfig;
import org.ruoyi.codereview.event.MergeRequestReviewedEvent;
import org.ruoyi.codereview.event.PushReviewedEvent;
import org.ruoyi.codereview.event.ReviewFailedEvent;
import org.ruoyi.codereview.mapper.MrReviewLogMapper;
import org.ruoyi.codereview.mapper.PushReviewLogMapper;
import org.ruoyi.codereview.metrics.CodeReviewMetrics;
import org.ruoyi.codereview.platform.GitPlatformClient;
import org.ruoyi.codereview.platform.PlatformClientFactory;
import org.ruoyi.codereview.util.PromptTemplate;
import org.ruoyi.codereview.util.TokenCounter;
import org.ruoyi.common.chat.domain.vo.chat.ChatModelVo;
import org.ruoyi.common.chat.service.chat.IChatModelService;
import org.ruoyi.factory.ChatServiceFactory;
import org.ruoyi.service.chat.AbstractChatService;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 代码审查服务
 * <p>
 * 支持项目级别配置：
 * - 不同项目可使用不同的 AI 模型
 * - 不同项目可配置不同的审查风格、Token 限制等
 * - 不同项目可配置不同的 Git 平台 URL 和 Token
 * - 事件携带通知渠道配置，支持项目级通知
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeReviewService {

    private final CodeReviewProperties properties;
    private final MrReviewLogMapper mrReviewLogMapper;
    private final PushReviewLogMapper pushReviewLogMapper;
    private final IChatModelService chatModelService;
    private final ChatServiceFactory chatServiceFactory;
    private final Map<String, GitPlatformClient> platformClients;  // YAML 静态配置的客户端（fallback）
    private final ApplicationEventPublisher eventPublisher;
    private final ReviewConfigService configService;
    private final PlatformClientFactory platformClientFactory;
    private final IncrementalReviewService incrementalReviewService;
    private final CodeReviewMetrics metrics;
    private final ReviewStatisticsService statisticsService;

    /**
     * 获取平台客户端
     * <p>
     * 优先级：
     * 1. 项目配置中的平台 URL 和 Token
     * 2. YAML 静态配置的客户端
     */
    private GitPlatformClient getClient(String platform, ProjectConfig projectConfig) {
        // 1. 尝试从项目配置获取平台配置
        if (projectConfig != null) {
            PlatformConfig platformConfig = configService.getPlatformConfig(
                projectConfig.getProjectName(), platform);
            if (platformConfig != null && platformConfig.isEnabled()) {
                log.info("使用项目配置的平台客户端: project={}, platform={}",
                    projectConfig.getProjectName(), platform);
                return platformClientFactory.createClient(platformConfig);
            }
        }

        // 2. fallback 到 YAML 静态配置的客户端
        for (GitPlatformClient client : platformClients.values()) {
            if (platform.equals(client.getPlatform())) {
                log.debug("使用 YAML 配置的平台客户端: platform={}", platform);
                return client;
            }
        }

        throw new IllegalArgumentException("不支持的平台或平台未配置: " + platform);
    }

    /**
     * 获取平台客户端（无项目配置，使用 fallback）
     */
    private GitPlatformClient getClient(String platform) {
        for (GitPlatformClient client : platformClients.values()) {
            if (platform.equals(client.getPlatform())) {
                return client;
            }
        }
        throw new IllegalArgumentException("不支持的平台: " + platform);
    }

    // ==================== 文件过滤（支持项目配置） ====================

    private Set<String> getSupportedExtensions(ProjectConfig projectConfig) {
        String extensions = projectConfig != null && projectConfig.getFileExtensions() != null
            ? projectConfig.getFileExtensions()
            : properties.getReview().getExtensions();
        return Arrays.stream(extensions.split(","))
            .map(String::trim)
            .collect(Collectors.toSet());
    }

    /**
     * 获取排除文件模式
     */
    private List<String> getExcludePatterns(ProjectConfig projectConfig) {
        if (projectConfig == null || projectConfig.getExcludePatterns() == null
            || projectConfig.getExcludePatterns().isEmpty()) {
            return List.of();
        }
        return Arrays.stream(projectConfig.getExcludePatterns().split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
    }

    /**
     * 检查文件路径是否匹配排除模式
     */
    private boolean matchesExcludePattern(String filePath, List<String> patterns) {
        if (patterns.isEmpty() || filePath == null) {
            return false;
        }
        String normalizedPath = filePath.replace('\\', '/');

        for (String pattern : patterns) {
            String normalizedPattern = pattern.replace('\\', '/');

            // 支持 Ant 风格通配符
            if (normalizedPattern.contains("*")) {
                // 简单通配符匹配
                String regex = normalizedPattern
                    .replace(".", "\\.")
                    .replace("**", ".*")
                    .replace("*", "[^/]*");
                if (normalizedPath.matches(regex)) {
                    return true;
                }
            } else {
                // 精确匹配或后缀匹配
                if (normalizedPath.equals(normalizedPattern)
                    || normalizedPath.endsWith(normalizedPattern)
                    || normalizedPath.contains(normalizedPattern)) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<CodeChange> filterChanges(List<CodeChange> changes, ProjectConfig projectConfig) {
        Set<String> extensions = getSupportedExtensions(projectConfig);
        List<String> excludePatterns = getExcludePatterns(projectConfig);

        return changes.stream()
            .filter(c -> !"delete".equals(c.getChangeType()))
            .filter(c -> {
                String path = c.getFilePath();
                if (path == null || path.isEmpty()) return false;
                int dotIdx = path.lastIndexOf('.');
                if (dotIdx < 0) return false;
                return extensions.contains(path.substring(dotIdx));
            })
            .filter(c -> !matchesExcludePattern(c.getFilePath(), excludePatterns))  // 排除文件模式
            .collect(Collectors.toList());
    }

    // ==================== 项目配置辅助方法 ====================

    /**
     * 判断审查是否启用
     */
    private boolean isReviewEnabled(ProjectConfig config, String eventType) {
        if (config == null) return true;  // 无配置时默认启用
        if ("push".equals(eventType)) {
            return config.getPushReviewEnabled() == null || config.getPushReviewEnabled() == 1;
        }
        return config.getReviewEnabled() == null || config.getReviewEnabled() == 1;
    }

    /**
     * 判断是否只审查受保护分支
     */
    private boolean isProtectedBranchesOnly(ProjectConfig config) {
        if (config != null && config.getProtectedBranchesOnly() != null) {
            return config.getProtectedBranchesOnly() == 1;
        }
        return properties.getReview().isMergeReviewOnlyProtectedBranches();
    }

    /**
     * 获取审查风格
     */
    private String getReviewStyle(ProjectConfig config) {
        if (config != null && config.getReviewStyle() != null && !config.getReviewStyle().isEmpty()) {
            return config.getReviewStyle();
        }
        return properties.getReview().getStyle();
    }

    /**
     * 获取最大 Token 数
     */
    private int getMaxTokens(ProjectConfig config) {
        if (config != null && config.getMaxTokens() != null && config.getMaxTokens() > 0) {
            return config.getMaxTokens();
        }
        return properties.getReview().getMaxTokens();
    }

    // ==================== LLM 调用（支持项目配置模型） ====================

    /**
     * 调用 LLM 进行代码审查
     * <p>
     * 支持重试机制：网络抖动或临时错误时自动重试
     */
    @org.springframework.retry.annotation.Retryable(
        retryFor = {RuntimeException.class},
        maxAttempts = 3,
        backoff = @org.springframework.retry.annotation.Backoff(delay = 2000, multiplier = 2)
    )
    private String callLlm(String systemPrompt, String userPrompt, ProjectConfig projectConfig) {
        // 优先使用项目配置的模型
        Long modelId = projectConfig != null ? projectConfig.getModelId() : null;
        ChatModelVo chatModelVo;

        if (modelId != null) {
            chatModelVo = chatModelService.queryById(modelId);
            log.info("使用项目配置模型: id={}", modelId);
        } else {
            // fallback 到全局配置
            String modelName = properties.getLlm().getModelName();
            if (modelName.matches("\\d+")) {
                Long id = Long.parseLong(modelName);
                chatModelVo = chatModelService.queryById(id);
                log.info("使用全局模型配置(ID): id={}", id);
            } else {
                chatModelVo = chatModelService.selectModelByName(modelName);
                log.info("使用全局模型配置(名称): name={}", modelName);
            }
        }

        if (chatModelVo == null) {
            throw new IllegalStateException("模型未找到，请在模型管理中配置该模型");
        }

        AbstractChatService chatService = chatServiceFactory.getOriginalService(chatModelVo.getProviderCode());
        ChatModel chatModel = chatService.buildChatModel(chatModelVo);

        ChatRequest chatRequest = ChatRequest.builder()
            .messages(SystemMessage.from(systemPrompt), UserMessage.from(userPrompt))
            .build();
        ChatResponse response = chatModel.chat(chatRequest);
        return response.aiMessage().text();
    }

    // ==================== GitLab 处理 ====================

    @Async("codeReviewExecutor")
    public void handleGitLabMergeRequest(String payload) {
        log.info("处理 GitLab Merge Request");
        handleMrEvent(payload, "gitlab");
    }

    @Async("codeReviewExecutor")
    public void handleGitLabPush(String payload) {
        log.info("处理 GitLab Push");
        handlePushEvent(payload, "gitlab");
    }

    // ==================== GitHub 处理 ====================

    @Async("codeReviewExecutor")
    public void handleGitHubPullRequest(String payload) {
        log.info("处理 GitHub Pull Request");
        handleMrEvent(payload, "github");
    }

    @Async("codeReviewExecutor")
    public void handleGitHubPush(String payload) {
        log.info("处理 GitHub Push");
        handlePushEvent(payload, "github");
    }

    // ==================== Gitea 处理 ====================

    @Async("codeReviewExecutor")
    public void handleGiteaPullRequest(String payload) {
        log.info("处理 Gitea Pull Request");
        handleMrEvent(payload, "gitea");
    }

    @Async("codeReviewExecutor")
    public void handleGiteaPush(String payload) {
        log.info("处理 Gitea Push");
        handlePushEvent(payload, "gitea");
    }

    // ==================== 通用 MR/PR 处理 ====================

    private void handleMrEvent(String payload, String platform) {
        long startTime = System.currentTimeMillis();
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        MDC.put("traceId", traceId);
        String projectName = "unknown";
        ProjectConfig projectConfig = null;
        try {
            log.info("[{}] 开始处理 MR/PR 事件, traceId={}", platform, traceId);
            JSONObject json = JSONUtil.parseObj(payload);

            // 先用 fallback 客户端提取基本信息（用于获取项目名）
            GitPlatformClient tempClient = getClient(platform);

            // Draft 检测
            if (tempClient.isDraft(json)) {
                log.info("[{}] Draft MR/PR，跳过审查", platform);
                return;
            }

            // 提取基本信息
            MrEventInfo info = extractMrInfo(json, platform);
            if (info == null) {
                log.warn("[{}] 无法提取 MR/PR 信息", platform);
                return;
            }
            projectName = info.projectName;

            // 【新增】获取项目配置
            projectConfig = configService.getProjectConfig(info.projectName, platform);
            log.info("[{}] 项目配置: {}", platform, projectConfig != null ? "已加载" : "使用全局配置");

            // 【修改】使用项目配置或全局配置判断是否启用
            if (!isReviewEnabled(projectConfig, "mr")) {
                log.info("[{}] MR 审查已禁用: {}", platform, info.projectName);
                return;
            }

            // 【修改】获取正确的客户端（可能使用项目配置的平台 URL 和 Token）
            GitPlatformClient client = getClient(platform, projectConfig);

            // 【修改】使用项目配置判断受保护分支
            if (isProtectedBranchesOnly(projectConfig)) {
                if (!client.isBranchProtected(json)) {
                    log.info("[{}] 目标分支非受保护分支，跳过审查", platform);
                    return;
                }
            }

            if (isAlreadyReviewed(info.projectName, info.lastCommitId, platform)) {
                log.info("[{}] MR/PR 已审查过，跳过: {}", platform, info.url);
                return;
            }

            // 获取代码变更
            List<CodeChange> changes = client.fetchMrChanges(json);
            changes = filterChanges(changes, projectConfig);  // 【修改】传入项目配置
            if (changes.isEmpty()) {
                log.info("[{}] 没有需要审查的代码变更", platform);
                return;
            }

            // 获取 commit 信息
            String commitMessages = client.fetchCommitMessages(json);

            // 【修改】传入项目配置
            ReviewResult result = reviewCode(changes, commitMessages, projectConfig);

            // 回写评论到 MR/PR
            client.postMrComment(json, formatReviewComment(result));

            // 保存记录
            saveMrReviewLog(info.projectName, info.author, info.sourceBranch, info.targetBranch,
                info.url, info.lastCommitId, result.reviewText, result.score, changes, platform, info.projectId);

            // 【修改】事件携带平台和通知配置
            eventPublisher.publishEvent(new MergeRequestReviewedEvent(this,
                info.projectName, platform, info.author, info.sourceBranch, info.targetBranch,
                info.url, result.score, result.reviewText,
                projectConfig != null ? projectConfig.getNotificationChannels() : null));

            // 【新增】记录监控指标
            long duration = System.currentTimeMillis() - startTime;
            String modelId = projectConfig != null && projectConfig.getModelId() != null
                ? projectConfig.getModelId().toString() : properties.getLlm().getModelName();
            metrics.recordReviewComplete(platform, "mr", result.score, changes.size(),
                TokenCounter.estimateTokens(commitMessages), TokenCounter.estimateTokens(result.reviewText),
                duration, modelId);

            // 更新统计数据
            try {
                statisticsService.updateTodayStatistics(info.projectName, platform);
            } catch (Exception ex) {
                log.warn("[{}] 更新统计数据失败", platform, ex);
            }

        } catch (Exception e) {
            log.error("[{}] 处理 MR/PR 事件失败", platform, e);
            metrics.recordReviewFailure(platform, "mr", e.getClass().getSimpleName());
            eventPublisher.publishEvent(new ReviewFailedEvent(this,
                projectName, platform, "MR/PR", e.getMessage(), payload));
        } finally {
            MDC.remove("traceId");
        }
    }

    // ==================== 通用 Push 处理 ====================

    private void handlePushEvent(String payload, String platform) {
        long startTime = System.currentTimeMillis();
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        MDC.put("traceId", traceId);
        String projectName = "unknown";
        ProjectConfig projectConfig = null;
        try {
            log.info("[{}] 开始处理 Push 事件, traceId={}", platform, traceId);
            JSONObject json = JSONUtil.parseObj(payload);
            String before = json.getStr("before");
            if ("0000000000000000000000000000000000000000".equals(before)) {
                log.info("[{}] 新分支推送，跳过", platform);
                return;
            }

            // 【新增】先提取项目信息，再获取项目配置
            PushEventInfo info = extractPushInfo(json, platform);
            if (info == null) {
                log.warn("[{}] 无法提取 Push 信息", platform);
                return;
            }
            projectName = info.projectName;

            // 【新增】获取项目配置
            projectConfig = configService.getProjectConfig(info.projectName, platform);
            log.info("[{}] 项目配置: {}", platform, projectConfig != null ? "已加载" : "使用全局配置");

            // 【修改】使用项目配置判断是否启用 Push 审查
            if (!isReviewEnabled(projectConfig, "push")) {
                log.info("[{}] Push 审查已禁用: {}", platform, info.projectName);
                return;
            }

            // 【修改】获取正确的客户端（可能使用项目配置的平台 URL 和 Token）
            GitPlatformClient client = getClient(platform, projectConfig);

            // 获取代码变更
            List<CodeChange> changes = client.fetchPushChanges(json);
            changes = filterChanges(changes, projectConfig);  // 【修改】传入项目配置
            if (changes.isEmpty()) {
                log.info("[{}] 没有需要审查的代码变更", platform);
                return;
            }

            // 获取 commit 信息
            String commitMessages = client.fetchCommitMessages(json);

            // 【修改】传入项目配置
            ReviewResult result = reviewCode(changes, commitMessages, projectConfig);

            // 回写评论到 commit
            client.postPushComment(json, formatReviewComment(result));

            // 保存记录
            savePushReviewLog(info.projectName, info.author, info.branch, commitMessages,
                result.reviewText, result.score, changes, platform, info.projectId);

            // 【修改】事件携带平台和通知配置
            eventPublisher.publishEvent(new PushReviewedEvent(this,
                info.projectName, platform, info.author, info.branch,
                result.score, result.reviewText,
                projectConfig != null ? projectConfig.getNotificationChannels() : null));

            // 【新增】记录监控指标
            long duration = System.currentTimeMillis() - startTime;
            String modelId = projectConfig != null && projectConfig.getModelId() != null
                ? projectConfig.getModelId().toString() : properties.getLlm().getModelName();
            metrics.recordReviewComplete(platform, "push", result.score, changes.size(),
                TokenCounter.estimateTokens(commitMessages), TokenCounter.estimateTokens(result.reviewText),
                duration, modelId);

            // 更新统计数据
            try {
                statisticsService.updateTodayStatistics(info.projectName, platform);
            } catch (Exception ex) {
                log.warn("[{}] 更新统计数据失败", platform, ex);
            }

        } catch (Exception e) {
            log.error("[{}] 处理 Push 事件失败", platform, e);
            metrics.recordReviewFailure(platform, "push", e.getClass().getSimpleName());
            eventPublisher.publishEvent(new ReviewFailedEvent(this,
                projectName, platform, "Push", e.getMessage(), payload));
        } finally {
            MDC.remove("traceId");
        }
    }

    // ==================== 信息提取 ====================

    private MrEventInfo extractMrInfo(JSONObject json, String platform) {
        MrEventInfo info = new MrEventInfo();
        switch (platform) {
            case "gitlab" -> {
                JSONObject project = json.getJSONObject("project");
                JSONObject attrs = json.getJSONObject("object_attributes");
                if (project == null || attrs == null) {
                    log.warn("GitLab MR payload 缺少必要字段");
                    return null;
                }
                JSONObject lastCommit = attrs.getJSONObject("last_commit");
                info.projectName = project.getStr("name");
                info.projectId = String.valueOf(project.getInt("id"));
                info.author = lastCommit != null ? lastCommit.getStr("author") : "unknown";
                info.sourceBranch = attrs.getStr("source_branch");
                info.targetBranch = attrs.getStr("target_branch");
                info.url = attrs.getStr("url");
                info.lastCommitId = attrs.getStr("last_commit_id");
            }
            case "github", "gitea" -> {
                JSONObject repo = json.getJSONObject("repository");
                JSONObject pr = json.getJSONObject("pull_request");
                if (repo == null || pr == null) {
                    log.warn("[{}] PR payload 缺少必要字段", platform);
                    return null;
                }
                JSONObject user = pr.getJSONObject("user");
                JSONObject head = pr.getJSONObject("head");
                JSONObject base = pr.getJSONObject("base");
                info.projectName = repo.getStr("name");
                info.projectId = String.valueOf(repo.getInt("id"));
                info.author = user != null ? user.getStr("login") : "unknown";
                info.sourceBranch = head != null ? head.getStr("ref") : "";
                info.targetBranch = base != null ? base.getStr("ref") : "";
                info.url = pr.getStr("html_url");
                info.lastCommitId = head != null ? head.getStr("sha") : "";
            }
            default -> { return null; }
        }
        return info;
    }

    private PushEventInfo extractPushInfo(JSONObject json, String platform) {
        PushEventInfo info = new PushEventInfo();
        JSONObject repo = json.getJSONObject("repository");
        if (repo == null) {
            log.warn("[{}] Push payload 缺少 repository 字段", platform);
            return null;
        }
        info.projectName = repo.getStr("name");
        info.projectId = String.valueOf(repo.getInt("id"));

        switch (platform) {
            case "gitlab" -> info.author = json.getStr("user_name", "unknown");
            case "github", "gitea" -> {
                JSONObject sender = json.getJSONObject("sender");
                info.author = sender != null ? sender.getStr("login") : "unknown";
            }
        }

        String ref = json.getStr("ref");
        info.branch = ref != null && ref.startsWith("refs/heads/") ? ref.substring(11) : ref;
        return info;
    }

    // ==================== 代码审查核心 ====================

    private ReviewResult reviewCode(List<CodeChange> changes, String commitMessages, ProjectConfig projectConfig) {
        // 【修改】使用项目配置
        String style = getReviewStyle(projectConfig);
        int maxTokens = getMaxTokens(projectConfig);

        // 【新增】支持自定义规则注入
        List<ReviewRule> customRules = null;
        if (projectConfig != null && projectConfig.getCustomRules() != null) {
            customRules = PromptTemplate.parseCustomRules(projectConfig.getCustomRules());
            if (!customRules.isEmpty()) {
                log.info("使用自定义审查规则: {} 条", customRules.size());
            }
        }

        // 如果没有项目级自定义规则，使用全局启用的规则
        if ((customRules == null || customRules.isEmpty())) {
            customRules = configService.getEnabledRules();
        }

        String systemPrompt = PromptTemplate.buildSystemPrompt(style, customRules);

        // 【新增】支持增量审查模式
        boolean incrementalEnabled = configService.getBoolean("review.incremental.enabled", true);
        String userPrompt;

        if (incrementalEnabled && changes.size() > 1) {
            // 使用增量审查：只审查变更的代码行
            List<IncrementalReviewService.IncrementalChange> incrementalChanges =
                incrementalReviewService.extractChangedLines(changes);
            userPrompt = incrementalReviewService.buildIncrementalPrompt(incrementalChanges, commitMessages);
            log.debug("使用增量审查模式，文件数: {}", changes.size());
        } else {
            // 传统模式：审查整个 diff
            userPrompt = PromptTemplate.buildUserPrompt(changes, commitMessages);
        }

        // Token 截断
        if (TokenCounter.estimateTokens(userPrompt) > maxTokens) {
            userPrompt = TokenCounter.truncate(userPrompt, maxTokens);
            log.info("Prompt 已截断至 {} tokens", maxTokens);
        }

        // 【修改】传入项目配置
        String response = callLlm(systemPrompt, userPrompt, projectConfig);
        int score = PromptTemplate.extractScore(response);

        ReviewResult result = new ReviewResult();
        result.setReviewText(response);
        result.setScore(score);
        return result;
    }

    private String formatReviewComment(ReviewResult result) {
        return "## AI Code Review\n\n" + result.reviewText
            + "\n\n---\n*Powered by ruoyi-ai-codereview*";
    }

    // ==================== 辅助方法 ====================

    private boolean isAlreadyReviewed(String projectName, String lastCommitId, String platform) {
        if (lastCommitId == null || lastCommitId.isEmpty()) {
            return false;
        }
        return mrReviewLogMapper.selectCount(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MrReviewLog>()
                .eq(MrReviewLog::getProjectName, projectName)
                .eq(MrReviewLog::getLastCommitId, lastCommitId)
                .eq(MrReviewLog::getPlatform, platform)
        ) > 0;
    }

    @Transactional
    public void saveMrReviewLog(String projectName, String author, String sourceBranch, String targetBranch,
                                 String url, String lastCommitId, String reviewResult, int score,
                                 List<CodeChange> changes, String platform, String projectId) {
        MrReviewLog logEntity = new MrReviewLog();
        logEntity.setProjectName(projectName);
        logEntity.setAuthor(author);
        logEntity.setSourceBranch(sourceBranch);
        logEntity.setTargetBranch(targetBranch);
        logEntity.setUrl(url);
        logEntity.setLastCommitId(lastCommitId);
        logEntity.setReviewResult(reviewResult);
        logEntity.setScore(score);
        logEntity.setPlatform(platform);
        logEntity.setProjectId(projectId);
        logEntity.setCommitMessages("");
        int additions = 0, deletions = 0;
        for (CodeChange change : changes) {
            additions += change.getAdditions();
            deletions += change.getDeletions();
        }
        logEntity.setAdditions(additions);
        logEntity.setDeletions(deletions);

        mrReviewLogMapper.insert(logEntity);
    }

    @Transactional
    public void savePushReviewLog(String projectName, String author, String branch, String commitMessages,
                                   String reviewResult, int score, List<CodeChange> changes,
                                   String platform, String projectId) {
        PushReviewLog logEntity = new PushReviewLog();
        logEntity.setProjectName(projectName);
        logEntity.setAuthor(author);
        logEntity.setBranch(branch);
        logEntity.setCommitMessages(commitMessages);
        logEntity.setReviewResult(reviewResult);
        logEntity.setScore(score);
        logEntity.setPlatform(platform);
        logEntity.setProjectId(projectId);

        int additions = 0, deletions = 0;
        for (CodeChange change : changes) {
            additions += change.getAdditions();
            deletions += change.getDeletions();
        }
        logEntity.setAdditions(additions);
        logEntity.setDeletions(deletions);

        pushReviewLogMapper.insert(logEntity);
    }

    @Data
    public static class ReviewResult {
        private String reviewText;
        private int score;
    }

    @Data
    public static class MrEventInfo {
        private String projectName;
        private String projectId;
        private String author;
        private String sourceBranch;
        private String targetBranch;
        private String url;
        private String lastCommitId;
    }

    @Data
    public static class PushEventInfo {
        private String projectName;
        private String projectId;
        private String author;
        private String branch;
    }
}
