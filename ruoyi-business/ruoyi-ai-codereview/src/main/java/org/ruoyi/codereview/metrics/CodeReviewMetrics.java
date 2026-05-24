package org.ruoyi.codereview.metrics;

import io.micrometer.core.instrument.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 代码审查监控指标
 * <p>
 * 提供以下指标：
 * - codereview.requests: 审查请求计数（按平台、事件类型、成功/失败）
 * - codereview.llm.latency: LLM 调用延迟
 * - codereview.tokens.input: 输入 Token 数
 * - codereview.tokens.output: 输出 Token 数
 * - codereview.score: 评分分布
 * - codereview.files.reviewed: 审查文件数
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CodeReviewMetrics {

    private final MeterRegistry meterRegistry;

    // ==================== 计数器 ====================

    /**
     * 记录审查请求
     */
    public void recordReviewRequest(String platform, String eventType, boolean success) {
        Counter.builder("codereview.requests")
                .tag("platform", platform)
                .tag("event", eventType)
                .tag("success", String.valueOf(success))
                .description("Code review request count")
                .register(meterRegistry)
                .increment();
    }

    /**
     * 记录审查文件数
     */
    public void recordFilesReviewed(String platform, int fileCount) {
        Counter.builder("codereview.files.reviewed")
                .tag("platform", platform)
                .description("Number of files reviewed")
                .register(meterRegistry)
                .increment(fileCount);
    }

    /**
     * 记录输入 Token 数
     */
    public void recordInputTokens(String model, int tokens) {
        Counter.builder("codereview.tokens.input")
                .tag("model", model)
                .description("Input token count")
                .register(meterRegistry)
                .increment(tokens);
    }

    /**
     * 记录输出 Token 数
     */
    public void recordOutputTokens(String model, int tokens) {
        Counter.builder("codereview.tokens.output")
                .tag("model", model)
                .description("Output token count")
                .register(meterRegistry)
                .increment(tokens);
    }

    // ==================== 计时器 ====================

    /**
     * 开始 LLM 调用计时
     */
    public Timer.Sample startLlmTimer() {
        return Timer.start(meterRegistry);
    }

    /**
     * 记录 LLM 调用延迟
     */
    public void recordLlmLatency(Timer.Sample sample, String model) {
        sample.stop(Timer.builder("codereview.llm.latency")
                .tag("model", model)
                .description("LLM call latency")
                .publishPercentiles(0.5, 0.9, 0.95, 0.99)
                .register(meterRegistry));
    }

    /**
     * 记录平台 API 调用延迟
     */
    public void recordPlatformApiLatency(String platform, String operation, long durationMs) {
        Timer.builder("codereview.platform.latency")
                .tag("platform", platform)
                .tag("operation", operation)
                .description("Platform API call latency")
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    // ==================== 分布统计 ====================

    /**
     * 记录评分分布
     */
    public void recordScore(String platform, int score) {
        DistributionSummary.builder("codereview.score")
                .tag("platform", platform)
                .description("Code review score distribution")
                .baseUnit("points")
                .publishPercentiles(0.5, 0.9, 0.95, 0.99)
                .register(meterRegistry)
                .record(score);
    }

    // ==================== 仪表盘 ====================

    /**
     * 记录缓存大小
     */
    public void recordCacheSize(String cacheName, int size) {
        Gauge.builder("codereview.cache.size", () -> size)
                .tag("cache", cacheName)
                .description("Cache size")
                .register(meterRegistry);
    }

    /**
     * 记录活跃审查数
     */
    public void incrementActiveReviews() {
        AtomicLongGauge gauge = (AtomicLongGauge) meterRegistry.find("codereview.active.reviews").gauge();
        if (gauge == null) {
            Gauge.builder("codereview.active.reviews", AtomicLongGauge::get)
                    .description("Number of active reviews")
                    .register(meterRegistry);
        }
    }

    public void decrementActiveReviews() {
        // 通过 Gauge 自动更新
    }

    // ==================== 辅助类 ====================

    /**
     * 原子 Long 仪表盘包装类
     */
    private static class AtomicLongGauge {
        private static final java.util.concurrent.atomic.AtomicLong VALUE = new java.util.concurrent.atomic.AtomicLong(0);

        public static long get() {
            return VALUE.get();
        }

        public static void increment() {
            VALUE.incrementAndGet();
        }

        public static void decrement() {
            VALUE.decrementAndGet();
        }
    }

    // ==================== 便捷方法 ====================

    /**
     * 记录完整的审查指标
     */
    public void recordReviewComplete(String platform, String eventType, int score,
                                      int fileCount, int inputTokens, int outputTokens,
                                      long durationMs, String model) {
        recordReviewRequest(platform, eventType, true);
        recordScore(platform, score);
        recordFilesReviewed(platform, fileCount);
        recordInputTokens(model, inputTokens);
        recordOutputTokens(model, outputTokens);

        // 记录总体耗时
        Timer.builder("codereview.review.duration")
                .tag("platform", platform)
                .tag("event", eventType)
                .description("Total review duration")
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    /**
     * 记录审查失败
     */
    public void recordReviewFailure(String platform, String eventType, String errorType) {
        Counter.builder("codereview.requests")
                .tag("platform", platform)
                .tag("event", eventType)
                .tag("success", "false")
                .tag("error", errorType)
                .description("Code review request count")
                .register(meterRegistry)
                .increment();
    }
}
