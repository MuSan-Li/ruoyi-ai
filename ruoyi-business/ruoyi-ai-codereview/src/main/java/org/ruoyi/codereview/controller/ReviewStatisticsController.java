package org.ruoyi.codereview.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.codereview.entity.ReviewStatistics;
import org.ruoyi.codereview.service.ReviewStatisticsService;
import org.ruoyi.common.core.domain.R;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * 审查统计 Controller
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/codereview/statistics")
public class ReviewStatisticsController {

    private final ReviewStatisticsService statisticsService;

    /**
     * 获取趋势数据
     */
    @SaCheckPermission("codereview:statistics:query")
    @GetMapping("/trend")
    public R<List<ReviewStatistics>> getTrend(
            @RequestParam(required = false, defaultValue = "") String projectName,
            @RequestParam(required = false, defaultValue = "") String platform,
            @RequestParam(required = false, defaultValue = "") String author,
            @RequestParam(required = false, defaultValue = "7") Integer days) {
        List<ReviewStatistics> stats = statisticsService.getTrend(projectName, platform, author, days);
        return R.ok(stats);
    }

    /**
     * 生成趋势报告
     */
    @SaCheckPermission("codereview:statistics:query")
    @GetMapping("/report")
    public R<String> generateReport(
            @RequestParam(required = false, defaultValue = "") String projectName,
            @RequestParam(required = false, defaultValue = "") String platform,
            @RequestParam(required = false, defaultValue = "") String author,
            @RequestParam(required = false, defaultValue = "7") Integer days) {
        String report = statisticsService.generateTrendReport(projectName, platform, author, days);
        return R.ok("操作成功", report);
    }

    /**
     * 获取项目下的作者列表
     */
    @SaCheckPermission("codereview:statistics:query")
    @GetMapping("/authors")
    public R<Set<String>> getAuthors(
            @RequestParam(required = false, defaultValue = "") String projectName,
            @RequestParam(required = false, defaultValue = "") String platform) {
        return R.ok("操作成功", statisticsService.getDistinctAuthors(projectName, platform));
    }

    /**
     * 手动刷新统计数据（补齐历史数据）
     */
    @SaCheckPermission("codereview:statistics:edit")
    @PostMapping("/refresh")
    public R<Void> refreshStatistics(
            @RequestParam String projectName,
            @RequestParam String platform) {
        statisticsService.refreshAllStatistics(projectName, platform);
        return R.ok();
    }
}
