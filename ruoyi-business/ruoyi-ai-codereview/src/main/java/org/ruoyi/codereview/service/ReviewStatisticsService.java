package org.ruoyi.codereview.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.codereview.entity.MrReviewLog;
import org.ruoyi.codereview.entity.PushReviewLog;
import org.ruoyi.codereview.entity.ReviewStatistics;
import org.ruoyi.codereview.mapper.MrReviewLogMapper;
import org.ruoyi.codereview.mapper.PushReviewLogMapper;
import org.ruoyi.codereview.mapper.ReviewStatisticsMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 审查统计服务
 * 用于趋势分析和报表生成
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewStatisticsService {

    private final MrReviewLogMapper mrReviewLogMapper;
    /**
     * 维度分数提取模式
     */
    private static final Pattern FUNCTIONALITY_PATTERN = Pattern.compile("功能性[^0-9]*(\\d+)\\s*[/分]");
    private final ReviewStatisticsMapper statisticsMapper;
    private static final Pattern SECURITY_PATTERN = Pattern.compile("安全[^0-9]*(\\d+)\\s*[/分]");
    private static final Pattern BEST_PRACTICE_PATTERN = Pattern.compile("最佳实践[^0-9]*(\\d+)\\s*[/分]");
    private static final Pattern PERFORMANCE_PATTERN = Pattern.compile("性能[^0-9]*(\\d+)\\s*[/分]");
    private static final Pattern COMMIT_MESSAGE_PATTERN = Pattern.compile("[Cc]ommit[^0-9]*(\\d+)\\s*[/分]");
    private final PushReviewLogMapper pushReviewLogMapper;

    /**
     * 更新今日统计
     */
    public void updateTodayStatistics(String projectName, String platform) {
        LocalDate today = LocalDate.now();
        updateStatistics(projectName, platform, today);
    }

    private static int safeInt(Integer val) {
        return val != null ? val : 0;
    }

    private static BigDecimal safeAvg(List<BigDecimal> values) {
        if (values.isEmpty()) return BigDecimal.ZERO;
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(values.size()), 1, RoundingMode.HALF_UP);
    }

    /**
     * 更新指定日期统计
     */
    public void updateStatistics(String projectName, String platform, LocalDate date) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);

        // 查询 MR 审查日志
        List<MrReviewLog> mrLogs = mrReviewLogMapper.selectList(
            new LambdaQueryWrapper<MrReviewLog>()
                .eq(MrReviewLog::getProjectName, projectName)
                .eq(MrReviewLog::getPlatform, platform)
                .between(MrReviewLog::getCreateTime, startOfDay, endOfDay)
        );

        // 查询 Push 审查日志
        List<PushReviewLog> pushLogs = pushReviewLogMapper.selectList(
            new LambdaQueryWrapper<PushReviewLog>()
                .eq(PushReviewLog::getProjectName, projectName)
                .eq(PushReviewLog::getPlatform, platform)
                .between(PushReviewLog::getCreateTime, startOfDay, endOfDay)
        );

        if (mrLogs.isEmpty() && pushLogs.isEmpty()) {
            log.debug("无审查记录: project={}, platform={}, date={}", projectName, platform, date);
            return;
        }

        ReviewStatistics stats = calculateStatistics(projectName, platform, date, mrLogs, pushLogs);

        ReviewStatistics existing = statisticsMapper.selectOne(
            new LambdaQueryWrapper<ReviewStatistics>()
                .eq(ReviewStatistics::getProjectName, projectName)
                .eq(ReviewStatistics::getPlatform, platform)
                .eq(ReviewStatistics::getStatDate, date)
        );

        if (existing != null) {
            stats.setId(existing.getId());
            statisticsMapper.updateById(stats);
        } else {
            statisticsMapper.insert(stats);
        }

        log.info("统计更新完成: project={}, date={}, mrReviews={}, pushReviews={}",
            projectName, date, mrLogs.size(), pushLogs.size());
    }

    /**
     * 计算统计数据
     */
    private ReviewStatistics calculateStatistics(String projectName, String platform, LocalDate date,
                                                 List<MrReviewLog> mrLogs, List<PushReviewLog> pushLogs) {
        ReviewStatistics stats = new ReviewStatistics();
        stats.setProjectName(projectName);
        stats.setPlatform(platform);
        stats.setStatDate(date);
        stats.setTenantId("000000");

        // 合并 MR 和 Push 日志的分数（过滤 null）
        List<Integer> mrScores = mrLogs.stream().map(MrReviewLog::getScore).filter(Objects::nonNull).toList();
        List<Integer> pushScores = pushLogs.stream().map(PushReviewLog::getScore).filter(Objects::nonNull).toList();
        List<Integer> allScores = new ArrayList<>();
        allScores.addAll(mrScores);
        allScores.addAll(pushScores);

        // 基础统计
        int totalReviews = mrLogs.size() + pushLogs.size();
        stats.setTotalReviews(totalReviews);
        stats.setTotalFiles(totalReviews);
        stats.setTotalAdditions(
            mrLogs.stream().mapToInt(log -> log.getAdditions() != null ? log.getAdditions() : 0).sum() +
                pushLogs.stream().mapToInt(log -> log.getAdditions() != null ? log.getAdditions() : 0).sum()
        );
        stats.setTotalDeletions(
            mrLogs.stream().mapToInt(log -> log.getDeletions() != null ? log.getDeletions() : 0).sum() +
                pushLogs.stream().mapToInt(log -> log.getDeletions() != null ? log.getDeletions() : 0).sum()
        );

        // 分数统计
        stats.setAvgScore(BigDecimal.valueOf(allScores.stream().mapToInt(Integer::intValue).average().orElse(0))
            .setScale(2, RoundingMode.HALF_UP));
        stats.setMaxScore(allScores.stream().mapToInt(Integer::intValue).max().orElse(0));
        stats.setMinScore(allScores.stream().mapToInt(Integer::intValue).min().orElse(0));

        int passScore = 60;
        stats.setPassCount((int) allScores.stream().filter(s -> s >= passScore).count());
        stats.setFailCount((int) allScores.stream().filter(s -> s < passScore).count());

        // 维度分数统计 - 合并 MR 和 Push 的审查结果
        List<DimensionScores> allDimensionScores = new ArrayList<>();
        allDimensionScores.addAll(mrLogs.stream()
            .map(log -> extractDimensionScores(log.getReviewResult()))
            .filter(Objects::nonNull)
            .toList());
        allDimensionScores.addAll(pushLogs.stream()
            .map(log -> extractDimensionScores(log.getReviewResult()))
            .filter(Objects::nonNull)
            .toList());

        stats.setFunctionalityAvg(calculateAverage(allDimensionScores, "functionality"));
        stats.setSecurityAvg(calculateAverage(allDimensionScores, "security"));
        stats.setBestPracticeAvg(calculateAverage(allDimensionScores, "bestPractice"));
        stats.setPerformanceAvg(calculateAverage(allDimensionScores, "performance"));
        stats.setCommitMessageAvg(calculateAverage(allDimensionScores, "commitMessage"));

        return stats;
    }

    /**
     * 提取维度分数
     */
    private DimensionScores extractDimensionScores(String reviewResult) {
        if (reviewResult == null || reviewResult.isEmpty()) {
            return null;
        }

        DimensionScores scores = new DimensionScores();

        // 提取功能性分数
        Matcher m = FUNCTIONALITY_PATTERN.matcher(reviewResult);
        if (m.find()) {
            scores.functionality = new BigDecimal(m.group(1));
        }

        // 提取安全性分数
        m = SECURITY_PATTERN.matcher(reviewResult);
        if (m.find()) {
            scores.security = new BigDecimal(m.group(1));
        }

        // 提取最佳实践分数
        m = BEST_PRACTICE_PATTERN.matcher(reviewResult);
        if (m.find()) {
            scores.bestPractice = new BigDecimal(m.group(1));
        }

        // 提取性能分数
        m = PERFORMANCE_PATTERN.matcher(reviewResult);
        if (m.find()) {
            scores.performance = new BigDecimal(m.group(1));
        }

        // 提取 Commit Message 分数
        m = COMMIT_MESSAGE_PATTERN.matcher(reviewResult);
        if (m.find()) {
            scores.commitMessage = new BigDecimal(m.group(1));
        }

        return scores;
    }

    /**
     * 计算维度平均分
     */
    private BigDecimal calculateAverage(List<DimensionScores> allScores, String dimension) {
        List<BigDecimal> values = allScores.stream()
            .map(s -> switch (dimension) {
                case "functionality" -> s.functionality;
                case "security" -> s.security;
                case "bestPractice" -> s.bestPractice;
                case "performance" -> s.performance;
                case "commitMessage" -> s.commitMessage;
                default -> null;
            })
            .filter(Objects::nonNull)
            .toList();

        if (values.isEmpty()) {
            return BigDecimal.ZERO;
        }

        return values.stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }

    /**
     * 获取趋势数据（支持按作者筛选）
     */
    public List<ReviewStatistics> getTrend(String projectName, String platform, String author, int days) {
        if (author != null && !author.isEmpty()) {
            return computeTrendFromLogs(projectName, platform, author, days);
        }
        return getTrendFromCache(projectName, platform, days);
    }

    /**
     * 从缓存统计表获取趋势
     */
    private List<ReviewStatistics> getTrendFromCache(String projectName, String platform, int days) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days);

        LambdaQueryWrapper<ReviewStatistics> wrapper = new LambdaQueryWrapper<ReviewStatistics>()
            .between(ReviewStatistics::getStatDate, startDate, endDate)
            .orderByAsc(ReviewStatistics::getStatDate);

        if (projectName != null && !projectName.isEmpty()) {
            wrapper.eq(ReviewStatistics::getProjectName, projectName);
        }
        if (platform != null && !platform.isEmpty()) {
            wrapper.eq(ReviewStatistics::getPlatform, platform);
        }

        return statisticsMapper.selectList(wrapper);
    }

    /**
     * 从审查日志实时计算趋势（按作者筛选时使用）
     */
    private List<ReviewStatistics> computeTrendFromLogs(String projectName, String platform, String author, int days) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days);
        LocalDate current = startDate;
        List<ReviewStatistics> result = new ArrayList<>();

        while (!current.isAfter(endDate)) {
            LocalDateTime startOfDay = current.atStartOfDay();
            LocalDateTime endOfDay = current.atTime(LocalTime.MAX);

            var mrWrapper = new LambdaQueryWrapper<MrReviewLog>()
                .between(MrReviewLog::getCreateTime, startOfDay, endOfDay);
            var pushWrapper = new LambdaQueryWrapper<PushReviewLog>()
                .between(PushReviewLog::getCreateTime, startOfDay, endOfDay);

            if (projectName != null && !projectName.isEmpty()) {
                mrWrapper.eq(MrReviewLog::getProjectName, projectName);
                pushWrapper.eq(PushReviewLog::getProjectName, projectName);
            }
            if (platform != null && !platform.isEmpty()) {
                mrWrapper.eq(MrReviewLog::getPlatform, platform);
                pushWrapper.eq(PushReviewLog::getPlatform, platform);
            }
            mrWrapper.eq(MrReviewLog::getAuthor, author);
            pushWrapper.eq(PushReviewLog::getAuthor, author);

            List<MrReviewLog> mrLogs = mrReviewLogMapper.selectList(mrWrapper);
            List<PushReviewLog> pushLogs = pushReviewLogMapper.selectList(pushWrapper);

            if (!mrLogs.isEmpty() || !pushLogs.isEmpty()) {
                result.add(calculateStatistics(projectName, platform, current, mrLogs, pushLogs));
            }
            current = current.plusDays(1);
        }

        return result;
    }

    /**
     * 生成趋势报告（支持 platform 为空时汇总所有平台）
     */
    public String generateTrendReport(String projectName, String platform, String author, int days) {
        List<ReviewStatistics> stats = getTrend(projectName, platform, author, days);

        if (stats.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# 代码审查趋势报告\n\n");
        sb.append("**项目**: ").append(projectName).append("\n");
        sb.append("**平台**: ").append(platform != null && !platform.isEmpty() ? platform : "全部").append("\n");
        if (author != null && !author.isEmpty()) {
            sb.append("**作者**: ").append(author).append("\n");
        }
        sb.append("**统计周期**: 最近 ").append(days).append(" 天\n\n");

        // 总体统计
        int totalReviews = stats.stream().mapToInt(s -> safeInt(s.getTotalReviews())).sum();
        List<BigDecimal> scores = stats.stream()
            .map(ReviewStatistics::getAvgScore)
            .filter(Objects::nonNull)
            .toList();
        BigDecimal avgScore = scores.isEmpty() ? BigDecimal.ZERO
            : scores.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(scores.size()), 2, RoundingMode.HALF_UP);

        sb.append("## 总体统计\n");
        sb.append("- 总审查次数: ").append(totalReviews).append("\n");
        sb.append("- 平均分数: ").append(avgScore).append("\n");
        sb.append("- 最高分: ").append(stats.stream().mapToInt(s -> safeInt(s.getMaxScore())).max().orElse(0)).append("\n");
        sb.append("- 最低分: ").append(stats.stream().mapToInt(s -> safeInt(s.getMinScore())).min().orElse(0)).append("\n\n");

        // 维度分数趋势
        sb.append("## 维度分数趋势\n");
        sb.append("| 维度 | 平均分 |\n");
        sb.append("|------|--------|\n");

        BigDecimal avgFunc = safeAvg(stats.stream().map(ReviewStatistics::getFunctionalityAvg).filter(Objects::nonNull).toList());
        BigDecimal avgSec = safeAvg(stats.stream().map(ReviewStatistics::getSecurityAvg).filter(Objects::nonNull).toList());
        BigDecimal avgBP = safeAvg(stats.stream().map(ReviewStatistics::getBestPracticeAvg).filter(Objects::nonNull).toList());

        sb.append(String.format("| 功能性 | %.1f |\n", avgFunc.doubleValue()));
        sb.append(String.format("| 安全性 | %.1f |\n", avgSec.doubleValue()));
        sb.append(String.format("| 最佳实践 | %.1f |\n\n", avgBP.doubleValue()));

        // 趋势表格
        sb.append("## 每日趋势\n");
        sb.append("| 日期 | 审查次数 | 平均分 | 通过率 |\n");
        sb.append("|------|----------|--------|--------|\n");

        for (ReviewStatistics stat : stats) {
            int reviews = safeInt(stat.getTotalReviews());
            int passes = safeInt(stat.getPassCount());
            String passRate = reviews > 0
                ? String.format("%.1f%%", 100.0 * passes / reviews)
                : "N/A";
            BigDecimal dayAvg = stat.getAvgScore() != null ? stat.getAvgScore() : BigDecimal.ZERO;
            sb.append(String.format("| %s | %d | %.1f | %s |\n",
                stat.getStatDate(),
                reviews,
                dayAvg.doubleValue(),
                passRate));
        }

        return sb.toString();
    }

    /**
     * 定时更新所有项目统计（每天凌晨1点）
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void scheduledUpdateStatistics() {
        log.info("开始定时更新审查统计...");
        Set<String> projectKeys = collectAllProjectKeys();

        for (String key : projectKeys) {
            String[] parts = key.split(":");
            if (parts.length == 2) {
                try {
                    updateTodayStatistics(parts[0], parts[1]);
                } catch (Exception e) {
                    log.error("更新统计失败: {}", key, e);
                }
            }
        }

        log.info("审查统计更新完成，共更新 {} 个项目", projectKeys.size());
    }

    /**
     * 刷新指定项目的所有历史统计数据
     */
    public void refreshAllStatistics(String projectName, String platform) {
        // 查询该项目最早的审查记录日期
        MrReviewLog earliestMr = mrReviewLogMapper.selectOne(
            new LambdaQueryWrapper<MrReviewLog>()
                .eq(MrReviewLog::getProjectName, projectName)
                .eq(MrReviewLog::getPlatform, platform)
                .orderByAsc(MrReviewLog::getCreateTime)
                .last("LIMIT 1"));
        PushReviewLog earliestPush = pushReviewLogMapper.selectOne(
            new LambdaQueryWrapper<PushReviewLog>()
                .eq(PushReviewLog::getProjectName, projectName)
                .eq(PushReviewLog::getPlatform, platform)
                .orderByAsc(PushReviewLog::getCreateTime)
                .last("LIMIT 1"));

        LocalDate start = LocalDate.now();
        if (earliestMr != null && earliestMr.getCreateTime() != null) {
            start = earliestMr.getCreateTime().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        }
        if (earliestPush != null && earliestPush.getCreateTime() != null) {
            LocalDate pushDate = earliestPush.getCreateTime().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
            if (pushDate.isBefore(start)) {
                start = pushDate;
            }
        }

        LocalDate today = LocalDate.now();
        LocalDate current = start;
        while (!current.isAfter(today)) {
            try {
                updateStatistics(projectName, platform, current);
            } catch (Exception e) {
                log.warn("补齐统计失败: {} {}: {}", projectName, current, e.getMessage());
            }
            current = current.plusDays(1);
        }

        log.info("历史统计刷新完成: project={}, platform={}, 从 {} 到 {}",
            projectName, platform, start, today);
    }

    /**
     * 获取项目下的作者列表
     */
    public Set<String> getDistinctAuthors(String projectName, String platform) {
        Set<String> authors = new TreeSet<>();

        var mrWrapper = new LambdaQueryWrapper<MrReviewLog>()
            .select(MrReviewLog::getAuthor)
            .isNotNull(MrReviewLog::getAuthor)
            .ne(MrReviewLog::getAuthor, "");
        var pushWrapper = new LambdaQueryWrapper<PushReviewLog>()
            .select(PushReviewLog::getAuthor)
            .isNotNull(PushReviewLog::getAuthor)
            .ne(PushReviewLog::getAuthor, "");

        if (projectName != null && !projectName.isEmpty()) {
            mrWrapper.eq(MrReviewLog::getProjectName, projectName);
            pushWrapper.eq(PushReviewLog::getProjectName, projectName);
        }
        if (platform != null && !platform.isEmpty()) {
            mrWrapper.eq(MrReviewLog::getPlatform, platform);
            pushWrapper.eq(PushReviewLog::getPlatform, platform);
        }

        mrReviewLogMapper.selectList(mrWrapper).forEach(l -> authors.add(l.getAuthor()));
        pushReviewLogMapper.selectList(pushWrapper).forEach(l -> authors.add(l.getAuthor()));

        return authors;
    }

    private Set<String> collectAllProjectKeys() {
        Set<String> projectKeys = new HashSet<>();
        List<MrReviewLog> allMrLogs = mrReviewLogMapper.selectList(
            new LambdaQueryWrapper<MrReviewLog>()
                .isNotNull(MrReviewLog::getProjectName)
                .select(MrReviewLog::getProjectName, MrReviewLog::getPlatform)
        );
        allMrLogs.forEach(l -> projectKeys.add(l.getProjectName() + ":" + l.getPlatform()));

        List<PushReviewLog> allPushLogs = pushReviewLogMapper.selectList(
            new LambdaQueryWrapper<PushReviewLog>()
                .isNotNull(PushReviewLog::getProjectName)
                .select(PushReviewLog::getProjectName, PushReviewLog::getPlatform)
        );
        allPushLogs.forEach(l -> projectKeys.add(l.getProjectName() + ":" + l.getPlatform()));
        return projectKeys;
    }

    /**
     * 维度分数内部类
     */
    private static class DimensionScores {
        BigDecimal functionality = BigDecimal.ZERO;
        BigDecimal security = BigDecimal.ZERO;
        BigDecimal bestPractice = BigDecimal.ZERO;
        BigDecimal performance = BigDecimal.ZERO;
        BigDecimal commitMessage = BigDecimal.ZERO;
    }
}
