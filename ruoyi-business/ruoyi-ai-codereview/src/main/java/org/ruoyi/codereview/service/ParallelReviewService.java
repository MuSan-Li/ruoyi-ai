package org.ruoyi.codereview.service;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.codereview.entity.CodeChange;
import org.ruoyi.codereview.util.PromptTemplate;
import org.ruoyi.codereview.util.TokenCounter;
import org.ruoyi.common.chat.domain.vo.chat.ChatModelVo;
import org.ruoyi.factory.ChatServiceFactory;
import org.ruoyi.service.chat.AbstractChatService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * 并行审查服务
 * 支持多文件并行调用 LLM 进行审查
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParallelReviewService {

    private final ReviewConfigService configService;
    private final ChatServiceFactory chatServiceFactory;

    /** 默认并行线程数 */
    private static final int DEFAULT_PARALLEL_THREADS = 4;

    /** 单文件最大 Token */
    private static final int MAX_TOKENS_PER_FILE = 4000;

    /**
     * 并行审查多个文件
     *
     * @param changes       代码变更列表
     * @param commitMessages 提交信息
     * @param chatModelVo   模型配置
     * @return 审查结果列表
     */
    public List<FileReviewResult> reviewInParallel(List<CodeChange> changes, String commitMessages, ChatModelVo chatModelVo) {
        if (!configService.getBoolean("review.parallel.enabled", true)) {
            // 不启用并行，逐个处理
            return reviewSequentially(changes, commitMessages, chatModelVo);
        }

        int threads = configService.getInt("review.parallel.threads", DEFAULT_PARALLEL_THREADS);
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(threads, changes.size()));

        try {
            List<Future<FileReviewResult>> futures = new ArrayList<>();
            String systemPrompt = PromptTemplate.buildSystemPrompt(configService.getString("review.style", "professional"));

            for (CodeChange change : changes) {
                futures.add(executor.submit(() -> reviewSingleFile(change, commitMessages, systemPrompt, chatModelVo)));
            }

            List<FileReviewResult> results = new ArrayList<>();
            for (int i = 0; i < futures.size(); i++) {
                try {
                    FileReviewResult result = futures.get(i).get(60, TimeUnit.SECONDS);
                    results.add(result);
                } catch (TimeoutException e) {
                    log.warn("文件审查超时: {}", changes.get(i).getFilePath());
                    results.add(FileReviewResult.timeout(changes.get(i).getFilePath()));
                } catch (Exception e) {
                    log.error("文件审查失败: {}", changes.get(i).getFilePath(), e);
                    results.add(FileReviewResult.error(changes.get(i).getFilePath(), e.getMessage()));
                }
            }

            return results;
        } finally {
            executor.shutdown();
        }
    }

    /**
     * 顺序审查（不并行）
     */
    private List<FileReviewResult> reviewSequentially(List<CodeChange> changes, String commitMessages, ChatModelVo chatModelVo) {
        List<FileReviewResult> results = new ArrayList<>();
        String systemPrompt = PromptTemplate.buildSystemPrompt(configService.getString("review.style", "professional"));

        for (CodeChange change : changes) {
            try {
                FileReviewResult result = reviewSingleFile(change, commitMessages, systemPrompt, chatModelVo);
                results.add(result);
            } catch (Exception e) {
                log.error("文件审查失败: {}", change.getFilePath(), e);
                results.add(FileReviewResult.error(change.getFilePath(), e.getMessage()));
            }
        }

        return results;
    }

    /**
     * 审查单个文件
     */
    private FileReviewResult reviewSingleFile(CodeChange change, String commitMessages, String systemPrompt, ChatModelVo chatModelVo) {
        String filePath = change.getFilePath();
        log.debug("开始审查文件: {}", filePath);

        // 构建用户提示
        String userPrompt = buildFilePrompt(change, commitMessages);

        // Token 截断
        if (TokenCounter.estimateTokens(userPrompt) > MAX_TOKENS_PER_FILE) {
            userPrompt = TokenCounter.truncate(userPrompt, MAX_TOKENS_PER_FILE);
            log.debug("文件 {} Prompt 已截断", filePath);
        }

        // 调用 LLM
        long startTime = System.currentTimeMillis();
        String response = callLlm(systemPrompt, userPrompt, chatModelVo);
        long duration = System.currentTimeMillis() - startTime;

        // 提取分数
        int score = PromptTemplate.extractScore(response);

        log.info("文件审查完成: {} (分数: {}, 耗时: {}ms)", filePath, score, duration);

        return FileReviewResult.success(filePath, response, score, duration);
    }

    /**
     * 构建单文件审查提示
     */
    private String buildFilePrompt(CodeChange change, String commitMessages) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 审查文件\n");
        sb.append("**文件路径**: ").append(change.getFilePath()).append("\n");
        sb.append("**变更类型**: ").append(change.getChangeType()).append("\n");
        sb.append("**新增行数**: ").append(change.getAdditions()).append("\n");
        sb.append("**删除行数**: ").append(change.getDeletions()).append("\n\n");

        sb.append("## 代码变更\n");
        sb.append("```diff\n").append(change.getDiff()).append("\n```\n\n");

        if (commitMessages != null && !commitMessages.isEmpty()) {
            sb.append("## 提交信息\n").append(commitMessages).append("\n");
        }

        return sb.toString();
    }

    /**
     * 调用 LLM
     */
    private String callLlm(String systemPrompt, String userPrompt, ChatModelVo chatModelVo) {
        AbstractChatService chatService = chatServiceFactory.getOriginalService(chatModelVo.getProviderCode());
        ChatModel chatModel = chatService.buildChatModel(chatModelVo);

        ChatRequest chatRequest = ChatRequest.builder()
            .messages(SystemMessage.from(systemPrompt), UserMessage.from(userPrompt))
            .build();

        ChatResponse response = chatModel.chat(chatRequest);
        return response.aiMessage().text();
    }

    /**
     * 合并多文件审查结果
     */
    public String mergeResults(List<FileReviewResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 代码审查报告\n\n");

        int totalScore = 0;
        int successCount = 0;

        for (FileReviewResult result : results) {
            if (result.isSuccess()) {
                totalScore += result.getScore();
                successCount++;
            }
        }

        int avgScore = successCount > 0 ? totalScore / successCount : 0;
        sb.append("## 总体评分: ").append(avgScore).append("/100\n\n");

        // 按分数排序，问题文件排在前面
        results.stream()
            .sorted((a, b) -> Integer.compare(a.getScore(), b.getScore()))
            .forEach(result -> {
                sb.append("### ").append(result.getFilePath()).append("\n");
                sb.append("**分数**: ").append(result.getScore()).append("/100\n");
                if (!result.isSuccess()) {
                    sb.append("**状态**: ").append(result.getError()).append("\n");
                }
                sb.append("\n");
            });

        return sb.toString();
    }

    /**
     * 单文件审查结果
     */
    public static class FileReviewResult {
        private final String filePath;
        private final String reviewText;
        private final int score;
        private final long durationMs;
        private final boolean success;
        private final String error;

        private FileReviewResult(String filePath, String reviewText, int score, long durationMs, boolean success, String error) {
            this.filePath = filePath;
            this.reviewText = reviewText;
            this.score = score;
            this.durationMs = durationMs;
            this.success = success;
            this.error = error;
        }

        public static FileReviewResult success(String filePath, String reviewText, int score, long durationMs) {
            return new FileReviewResult(filePath, reviewText, score, durationMs, true, null);
        }

        public static FileReviewResult error(String filePath, String error) {
            return new FileReviewResult(filePath, null, 0, 0, false, error);
        }

        public static FileReviewResult timeout(String filePath) {
            return new FileReviewResult(filePath, null, 0, 0, false, "审查超时");
        }

        public String getFilePath() { return filePath; }
        public String getReviewText() { return reviewText; }
        public int getScore() { return score; }
        public long getDurationMs() { return durationMs; }
        public boolean isSuccess() { return success; }
        public String getError() { return error; }
    }
}
