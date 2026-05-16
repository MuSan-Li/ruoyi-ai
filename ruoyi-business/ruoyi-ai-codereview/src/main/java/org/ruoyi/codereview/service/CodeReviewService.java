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
import org.ruoyi.codereview.entity.CodeChange;
import org.ruoyi.codereview.entity.MrReviewLog;
import org.ruoyi.codereview.entity.PushReviewLog;
import org.ruoyi.codereview.event.MergeRequestReviewedEvent;
import org.ruoyi.codereview.event.PushReviewedEvent;
import org.ruoyi.codereview.event.ReviewFailedEvent;
import org.ruoyi.codereview.mapper.MrReviewLogMapper;
import org.ruoyi.codereview.mapper.PushReviewLogMapper;
import org.ruoyi.codereview.platform.GitPlatformClient;
import org.ruoyi.codereview.util.PromptTemplate;
import org.ruoyi.codereview.util.TokenCounter;
import org.ruoyi.common.chat.domain.vo.chat.ChatModelVo;
import org.ruoyi.common.chat.service.chat.IChatModelService;
import org.ruoyi.factory.ChatServiceFactory;
import org.ruoyi.service.chat.AbstractChatService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 代码审查服务
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
    private final Map<String, GitPlatformClient> platformClients;
    private final ApplicationEventPublisher eventPublisher;

    private GitPlatformClient getClient(String platform) {
        for (GitPlatformClient client : platformClients.values()) {
            if (platform.equals(client.getPlatform())) {
                return client;
            }
        }
        throw new IllegalArgumentException("不支持的平台: " + platform);
    }

    private Set<String> getSupportedExtensions() {
        return Arrays.stream(properties.getReview().getExtensions().split(","))
            .map(String::trim)
            .collect(Collectors.toSet());
    }

    private List<CodeChange> filterChanges(List<CodeChange> changes) {
        Set<String> extensions = getSupportedExtensions();
        return changes.stream()
            .filter(c -> !"delete".equals(c.getChangeType()))
            .filter(c -> {
                String path = c.getFilePath();
                if (path == null || path.isEmpty()) return false;
                int dotIdx = path.lastIndexOf('.');
                if (dotIdx < 0) return false;
                return extensions.contains(path.substring(dotIdx));
            })
            .collect(Collectors.toList());
    }

    // ==================== LLM 调用 ====================

    private String callLlm(String systemPrompt, String userPrompt) {
        String modelName = properties.getLlm().getModelName();
        ChatModelVo chatModelVo;

        // 支持通过 ID 或名称查找模型
        // 如果 modelName 是纯数字，则按 ID 查找；否则按名称查找
        if (modelName.matches("\\d+")) {
            Long modelId = Long.parseLong(modelName);
            chatModelVo = chatModelService.queryById(modelId);
            log.info("通过 ID 查找模型: id={}, result={}", modelId, chatModelVo != null ? chatModelVo.getModelName() : "null");
        } else {
            chatModelVo = chatModelService.selectModelByName(modelName);
            log.info("通过名称查找模型: name={}, result={}", modelName, chatModelVo != null ? "found" : "null");
        }

        if (chatModelVo == null) {
            throw new IllegalStateException("模型未找到: " + modelName + "，请在模型管理中配置该模型");
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

    @Async
    public void handleGitLabMergeRequest(String payload) {
        log.info("处理 GitLab Merge Request");
        handleMrEvent(payload, "gitlab");
    }

    @Async
    public void handleGitLabPush(String payload) {
        log.info("处理 GitLab Push");
        handlePushEvent(payload, "gitlab");
    }

    // ==================== GitHub 处理 ====================

    @Async
    public void handleGitHubPullRequest(String payload) {
        log.info("处理 GitHub Pull Request");
        handleMrEvent(payload, "github");
    }

    @Async
    public void handleGitHubPush(String payload) {
        log.info("处理 GitHub Push");
        handlePushEvent(payload, "github");
    }

    // ==================== Gitea 处理 ====================

    @Async
    public void handleGiteaPullRequest(String payload) {
        log.info("处理 Gitea Pull Request");
        handleMrEvent(payload, "gitea");
    }

    @Async
    public void handleGiteaPush(String payload) {
        log.info("处理 Gitea Push");
        handlePushEvent(payload, "gitea");
    }

    // ==================== 通用 MR/PR 处理 ====================

    private void handleMrEvent(String payload, String platform) {
        String projectName = "unknown";
        try {
            JSONObject json = JSONUtil.parseObj(payload);
            GitPlatformClient client = getClient(platform);

            // Draft 检测
            if (client.isDraft(json)) {
                log.info("[{}] Draft MR/PR，跳过审查", platform);
                return;
            }

            // 受保护分支检查
            if (properties.getReview().isMergeReviewOnlyProtectedBranches()) {
                if (!client.isBranchProtected(json)) {
                    log.info("[{}] 目标分支非受保护分支，跳过审查", platform);
                    return;
                }
            }

            // 提取基本信息
            MrEventInfo info = extractMrInfo(json, platform);
            if (info == null) {
                log.warn("[{}] 无法提取 MR/PR 信息", platform);
                return;
            }
            projectName = info.projectName;

            if (isAlreadyReviewed(info.projectName, info.lastCommitId, platform)) {
                log.info("[{}] MR/PR 已审查过，跳过: {}", platform, info.url);
                return;
            }

            // 获取代码变更
            List<CodeChange> changes = client.fetchMrChanges(json);
            changes = filterChanges(changes);
            if (changes.isEmpty()) {
                log.info("[{}] 没有需要审查的代码变更", platform);
                return;
            }

            // 获取 commit 信息
            String commitMessages = client.fetchCommitMessages(json);

            // AI 审查
            ReviewResult result = reviewCode(changes, commitMessages);

            // 回写评论到 MR/PR
            client.postMrComment(json, formatReviewComment(result));

            // 保存记录
            saveMrReviewLog(info.projectName, info.author, info.sourceBranch, info.targetBranch,
                info.url, info.lastCommitId, result.reviewText, result.score, changes, platform, info.projectId);

            // 发布事件（通知由事件监听器处理）
            eventPublisher.publishEvent(new MergeRequestReviewedEvent(this,
                info.projectName, info.author, info.sourceBranch, info.targetBranch,
                info.url, result.score, result.reviewText));

        } catch (Exception e) {
            log.error("[{}] 处理 MR/PR 事件失败", platform, e);
            // 发布失败事件
            eventPublisher.publishEvent(new ReviewFailedEvent(this,
                projectName, platform, "MR/PR", e.getMessage(), payload));
        }
    }

    // ==================== 通用 Push 处理 ====================

    private void handlePushEvent(String payload, String platform) {
        if (!properties.getReview().isPushEnabled()) {
            log.info("[{}] Push 审查已禁用", platform);
            return;
        }

        String projectName = "unknown";
        try {
            JSONObject json = JSONUtil.parseObj(payload);
            String before = json.getStr("before");
            if ("0000000000000000000000000000000000000000".equals(before)) {
                log.info("[{}] 新分支推送，跳过", platform);
                return;
            }

            GitPlatformClient client = getClient(platform);
            PushEventInfo info = extractPushInfo(json, platform);
            if (info == null) {
                log.warn("[{}] 无法提取 Push 信息", platform);
                return;
            }
            projectName = info.projectName;

            // 获取代码变更
            List<CodeChange> changes = client.fetchPushChanges(json);
            changes = filterChanges(changes);
            if (changes.isEmpty()) {
                log.info("[{}] 没有需要审查的代码变更", platform);
                return;
            }

            // 获取 commit 信息
            String commitMessages = client.fetchCommitMessages(json);

            // AI 审查
            ReviewResult result = reviewCode(changes, commitMessages);

            // 回写评论到 commit
            client.postPushComment(json, formatReviewComment(result));

            // 保存记录
            savePushReviewLog(info.projectName, info.author, info.branch, commitMessages,
                result.reviewText, result.score, changes, platform, info.projectId);

            // 发布事件（通知由事件监听器处理）
            eventPublisher.publishEvent(new PushReviewedEvent(this,
                info.projectName, info.author, info.branch, result.score, result.reviewText));

        } catch (Exception e) {
            log.error("[{}] 处理 Push 事件失败", platform, e);
            // 发布失败事件
            eventPublisher.publishEvent(new ReviewFailedEvent(this,
                projectName, platform, "Push", e.getMessage(), payload));
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

    private ReviewResult reviewCode(List<CodeChange> changes, String commitMessages) {
        String systemPrompt = PromptTemplate.buildSystemPrompt(properties.getReview().getStyle());
        String userPrompt = PromptTemplate.buildUserPrompt(changes, commitMessages);

        // Token 截断
        int maxTokens = properties.getReview().getMaxTokens();
        if (TokenCounter.estimateTokens(userPrompt) > maxTokens) {
            userPrompt = TokenCounter.truncate(userPrompt, maxTokens);
            log.info("Prompt 已截断至 {} tokens", maxTokens);
        }

        String response = callLlm(systemPrompt, userPrompt);
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
