package org.ruoyi.codereview.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.codereview.config.CodeReviewProperties;
import org.ruoyi.codereview.entity.MrReviewLog;
import org.ruoyi.codereview.entity.PushReviewLog;
import org.ruoyi.codereview.mapper.MrReviewLogMapper;
import org.ruoyi.codereview.mapper.PushReviewLogMapper;
import org.ruoyi.codereview.notifier.NotifierManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 日报服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyReportService {

    private final MrReviewLogMapper mrReviewLogMapper;
    private final PushReviewLogMapper pushReviewLogMapper;
    private final NotifierManager notifierManager;
    private final CodeReviewProperties properties;

    /**
     * 定时日报：工作日 18:00 自动生成并通知
     */
    @Scheduled(cron = "${codereview.report.cron:0 0 18 * * 1-5}")
    public void scheduledDailyReport() {
        log.info("开始生成定时日报");
        try {
            String report = generateDailyReportWithLlm(LocalDate.now());
            notifierManager.notifyDailyReport(report);
            log.info("定时日报发送完成");
        } catch (Exception e) {
            log.error("定时日报生成失败", e);
        }
    }

    /**
     * 使用 LLM 生成工作日报
     */
    public String generateDailyReportWithLlm(LocalDate date) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();

        List<MrReviewLog> mrLogs = mrReviewLogMapper.selectList(
            new LambdaQueryWrapper<MrReviewLog>()
                .ge(MrReviewLog::getUpdateTime, startOfDay)
                .lt(MrReviewLog::getUpdateTime, endOfDay)
                .orderByDesc(MrReviewLog::getUpdateTime)
        );

        List<PushReviewLog> pushLogs = pushReviewLogMapper.selectList(
            new LambdaQueryWrapper<PushReviewLog>()
                .ge(PushReviewLog::getUpdateTime, startOfDay)
                .lt(PushReviewLog::getUpdateTime, endOfDay)
                .orderByDesc(PushReviewLog::getUpdateTime)
        );

        if (mrLogs.isEmpty() && pushLogs.isEmpty()) {
            return "今日无代码审查记录";
        }

        // 先生成统计报告
        String statsReport = generateStatsReport(date, mrLogs, pushLogs);
        return statsReport;
    }

    /**
     * 手动触发日报
     */
    public String triggerDailyReport() {
        return generateDailyReportWithLlm(LocalDate.now());
    }

    private String generateStatsReport(LocalDate date, List<MrReviewLog> mrLogs, List<PushReviewLog> pushLogs) {
        StringBuilder report = new StringBuilder();
        report.append("# 代码审查日报\n\n");
        report.append("**日期**: ").append(date.format(DateTimeFormatter.ISO_LOCAL_DATE)).append("\n\n");

        // 统计概览
        report.append("## 审查概览\n\n");
        report.append("- MR/PR 审查数: ").append(mrLogs.size()).append("\n");
        report.append("- Push 审查数: ").append(pushLogs.size()).append("\n");

        int totalScore = 0;
        int totalCount = mrLogs.size() + pushLogs.size();
        for (MrReviewLog log : mrLogs) {
            totalScore += log.getScore() != null ? log.getScore() : 0;
        }
        for (PushReviewLog log : pushLogs) {
            totalScore += log.getScore() != null ? log.getScore() : 0;
        }
        int avgScore = totalCount > 0 ? totalScore / totalCount : 0;
        report.append("- 平均评分: ").append(avgScore).append("/100\n\n");

        // 按开发者汇总
        Map<String, DeveloperStats> devStats = new LinkedHashMap<>();
        for (MrReviewLog log : mrLogs) {
            DeveloperStats stats = devStats.computeIfAbsent(log.getAuthor(), k -> new DeveloperStats());
            stats.author = log.getAuthor();
            stats.mrCount++;
            stats.totalScore += log.getScore() != null ? log.getScore() : 70;
            stats.additions += log.getAdditions() != null ? log.getAdditions() : 0;
            stats.deletions += log.getDeletions() != null ? log.getDeletions() : 0;
        }
        for (PushReviewLog log : pushLogs) {
            DeveloperStats stats = devStats.computeIfAbsent(log.getAuthor(), k -> new DeveloperStats());
            stats.author = log.getAuthor();
            stats.pushCount++;
            stats.totalScore += log.getScore() != null ? log.getScore() : 70;
            stats.additions += log.getAdditions() != null ? log.getAdditions() : 0;
            stats.deletions += log.getDeletions() != null ? log.getDeletions() : 0;
        }

        if (!devStats.isEmpty()) {
            report.append("## 开发者统计\n\n");
            report.append("| 开发者 | MR/PR | Push | 平均分 | +行 | -行 |\n");
            report.append("|--------|-------|------|--------|-----|-----|\n");
            for (DeveloperStats stats : devStats.values()) {
                int total = stats.mrCount + stats.pushCount;
                int avg = total > 0 ? stats.totalScore / total : 0;
                report.append(String.format("| %s | %d | %d | %d | +%d | -%d |\n",
                    stats.author, stats.mrCount, stats.pushCount, avg, stats.additions, stats.deletions));
            }
            report.append("\n");
        }

        // 项目统计
        Map<String, ProjectStats> projStats = new LinkedHashMap<>();
        for (MrReviewLog log : mrLogs) {
            ProjectStats ps = projStats.computeIfAbsent(log.getProjectName(), k -> new ProjectStats());
            ps.name = log.getProjectName();
            ps.mrCount++;
            ps.totalScore += log.getScore() != null ? log.getScore() : 70;
        }
        for (PushReviewLog log : pushLogs) {
            ProjectStats ps = projStats.computeIfAbsent(log.getProjectName(), k -> new ProjectStats());
            ps.name = log.getProjectName();
            ps.pushCount++;
            ps.totalScore += log.getScore() != null ? log.getScore() : 70;
        }

        if (!projStats.isEmpty()) {
            report.append("## 项目统计\n\n");
            report.append("| 项目 | MR/PR | Push | 平均分 |\n");
            report.append("|------|-------|------|--------|\n");
            for (ProjectStats ps : projStats.values()) {
                int total = ps.mrCount + ps.pushCount;
                int avg = total > 0 ? ps.totalScore / total : 0;
                report.append(String.format("| %s | %d | %d | %d |\n",
                    ps.name, ps.mrCount, ps.pushCount, avg));
            }
            report.append("\n");
        }

        // 低分提醒
        report.append("## 需要关注的低分代码\n\n");
        List<String> lowScoreItems = new ArrayList<>();
        for (MrReviewLog log : mrLogs) {
            if (log.getScore() != null && log.getScore() < 60) {
                lowScoreItems.add(String.format("- [%s](%s) 评分: %d (%s→%s)",
                    log.getProjectName(), log.getUrl(), log.getScore(),
                    log.getSourceBranch(), log.getTargetBranch()));
            }
        }
        if (lowScoreItems.isEmpty()) {
            report.append("无低分代码\n");
        } else {
            for (String item : lowScoreItems) {
                report.append(item).append("\n");
            }
        }

        return report.toString();
    }

    @Data
    public static class DeveloperStats {
        private String author;
        private int mrCount;
        private int pushCount;
        private int totalScore;
        private long additions;
        private long deletions;
    }

    @Data
    public static class ProjectStats {
        private String name;
        private int mrCount;
        private int pushCount;
        private int totalScore;
    }
}
