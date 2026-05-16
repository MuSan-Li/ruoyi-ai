package org.ruoyi.codereview.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.codereview.entity.MrReviewLog;
import org.ruoyi.codereview.entity.ReviewStatistics;
import org.ruoyi.codereview.mapper.MrReviewLogMapper;
import org.ruoyi.codereview.mapper.ReviewStatisticsMapper;
import org.ruoyi.codereview.util.PromptTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 审查统计服务
 * 用于趋势分析和报表生成
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewStatisticsService {

    private final MrReviewLogMapper mrReviewLogMapper;
    private final ReviewStatisticsMapper statisticsMapper;

    /** 维度分数提取模式 */
    private static final Pattern DIMENSION_PATTERN = Pattern.compile("(\\d+)\\s*/\\s*(\\d+)");

    /**
     * 更新今日统计
     */
    public void updateTodayStatistics(String projectName, String platform) {
        LocalDate today = LocalDate.now();
        updateStatistics(projectName, platform, today);
    }

    /**
     * 更新指定日期统计
     */
    public void updateStatistics(String projectName, String platform, LocalDate date) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);

        // 查询当日审查记录
        List<MrReviewLog> logs = mrReviewLogMapper.selectList(
            new LambdaQueryWrapper<MrReviewLog>()
                .eq(MrReviewLog::getProjectName, projectName)
                .eq(MrReviewLog::getPlatform, platform)
                .between(MrReviewLog::getCreateTime, startOfDay, endOfDay)
        );

        if (logs.isEmpty()) {
            log.debug("无审查记录: project={}, platform={}, date={}", projectName, platform, date);
            return;
        }

        // 计算统计数据
        ReviewStatistics stats = calculateStatistics(projectName, platform, date, logs);

        // 保存或更新
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

        log.info("统计更新完成: project={}, date={}, reviews={}", projectName, date, logs.size());
    }

    /**
     * 计算统计数据
     */
    private ReviewStatistics calculateStatistics(String projectName, String platform, LocalDate date, List<MrReviewLog> logs) {
        ReviewStatistics stats = new ReviewStatistics();
        stats.setProjectName(projectName);
        stats.setPlatform(platform);
        stats.setStatDate(date);
        stats.setTenantId("000000");

        // 基础统计
        stats.setTotalReviews(logs.size());
        stats.setTotalFiles(logs.stream().mapToInt(l -> 1).sum()); // 简化，实际应统计文件数
        stats.setTotalAdditions(logs.stream().mapToInt(MrReviewLog::getAdditions).sum());
        stats.setTotalDeletions(logs.stream().mapToInt(MrReviewLog::getDeletions).sum());

        // 分数统计
        List<Integer> scores = logs.stream().map(MrReviewLog::getScore).toList();
        stats.setAvgScore(BigDecimal.valueOf(scores.stream().mapToInt(Integer::intValue).average().orElse(0))
            .setScale(2, RoundingMode.HALF_UP));
        stats.setMaxScore(scores.stream().mapToInt(Integer::intValue).max().orElse(0));
        stats.setMinScore(scores.stream().mapToInt(Integer::intValue).min().orElse(0));

        int passScore = 60;
        stats.setPassCount((int) scores.stream().filter(s -> s >= passScore).count());
        stats.setFailCount((int) scores.stream().filter(s -> s < passScore).count());

        // 维度分数统计
        Map<String, List<BigDecimal>> dimensionScores = logs.stream()
            .collect(Collectors.groupingBy(
                l -> "all",
                Collectors.mapping(l -> extractDimensionScores(l.getReviewResult()), Collectors.toList())
            ));

        // 简化处理，实际应解析各维度分数
        stats.setFunctionalityAvg(BigDecimal.ZERO);
        stats.setSecurityAvg(BigDecimal.ZERO);
        stats.setBestPracticeAvg(BigDecimal.ZERO);
        stats.setPerformanceAvg(BigDecimal.ZERO);
        stats.setCommitMessageAvg(BigDecimal.ZERO);

        return stats;
    }

    /**
     * 提取维度分数（简化版）
     */
    private BigDecimal extractDimensionScores(String reviewResult) {
        if (reviewResult == null) return BigDecimal.ZERO;
        // 实际应解析各维度分数
        return BigDecimal.ZERO;
    }

    /**
     * 获取趋势数据
     */
    public List<ReviewStatistics> getTrend(String projectName, String platform, int days) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days);

        return statisticsMapper.selectList(
            new LambdaQueryWrapper<ReviewStatistics>()
                .eq(ReviewStatistics::getProjectName, projectName)
                .eq(ReviewStatistics::getPlatform, platform)
                .between(ReviewStatistics::getStatDate, startDate, endDate)
                .orderByAsc(ReviewStatistics::getStatDate)
        );
    }

    /**
     * 生成趋势报告
     */
    public String generateTrendReport(String projectName, String platform, int days) {
        List<ReviewStatistics> stats = getTrend(projectName, platform, days);

        if (stats.isEmpty()) {
            return "暂无统计数据";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# 代码审查趋势报告\n\n");
        sb.append("**项目**: ").append(projectName).append("\n");
        sb.append("**平台**: ").append(platform).append("\n");
        sb.append("**统计周期**: 最近 ").append(days).append(" 天\n\n");

        // 总体统计
        int totalReviews = stats.stream().mapToInt(ReviewStatistics::getTotalReviews).sum();
        BigDecimal avgScore = stats.stream()
            .map(ReviewStatistics::getAvgScore)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(stats.size()), 2, RoundingMode.HALF_UP);

        sb.append("## 总体统计\n");
        sb.append("- 总审查次数: ").append(totalReviews).append("\n");
        sb.append("- 平均分数: ").append(avgScore).append("\n");
        sb.append("- 最高分: ").append(stats.stream().mapToInt(ReviewStatistics::getMaxScore).max().orElse(0)).append("\n");
        sb.append("- 最低分: ").append(stats.stream().mapToInt(ReviewStatistics::getMinScore).min().orElse(0)).append("\n\n");

        // 趋势表格
        sb.append("## 每日趋势\n");
        sb.append("| 日期 | 审查次数 | 平均分 | 通过率 |\n");
        sb.append("|------|----------|--------|--------|\n");

        for (ReviewStatistics stat : stats) {
            String passRate = stat.getTotalReviews() > 0
                ? String.format("%.1f%%", 100.0 * stat.getPassCount() / stat.getTotalReviews())
                : "N/A";
            sb.append(String.format("| %s | %d | %.1f | %s |\n",
                stat.getStatDate(),
                stat.getTotalReviews(),
                stat.getAvgScore(),
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

        // 获取所有项目
        List<MrReviewLog> allLogs = mrReviewLogMapper.selectList(
            new LambdaQueryWrapper<MrReviewLog>()
                .isNotNull(MrReviewLog::getProjectName)
                .groupBy(MrReviewLog::getProjectName, MrReviewLog::getPlatform)
        );

        // 按项目和平台分组
        Map<String, List<MrReviewLog>> grouped = allLogs.stream()
            .collect(Collectors.groupingBy(l -> l.getProjectName() + ":" + l.getPlatform()));

        for (Map.Entry<String, List<MrReviewLog>> entry : grouped.entrySet()) {
            String[] parts = entry.getKey().split(":");
            if (parts.length == 2) {
                try {
                    updateTodayStatistics(parts[0], parts[1]);
                } catch (Exception e) {
                    log.error("更新统计失败: {}", entry.getKey(), e);
                }
            }
        }

        log.info("审查统计更新完成");
    }
}
