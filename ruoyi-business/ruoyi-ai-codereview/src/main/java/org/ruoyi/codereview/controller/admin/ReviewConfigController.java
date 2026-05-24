package org.ruoyi.codereview.controller.admin;

import lombok.RequiredArgsConstructor;
import org.ruoyi.codereview.entity.ProjectConfig;
import org.ruoyi.codereview.entity.ReviewConfig;
import org.ruoyi.codereview.entity.ReviewRule;
import org.ruoyi.codereview.notifier.NotifierManager;
import org.ruoyi.codereview.service.ProjectConfigService;
import org.ruoyi.codereview.service.ReviewConfigService;
import org.ruoyi.codereview.service.ReviewStatisticsService;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.common.mybatis.core.page.PageQuery;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 代码审查配置管理 Controller
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/codereview/admin/config")
public class ReviewConfigController {

    private final ReviewConfigService configService;
    private final ProjectConfigService projectConfigService;
    private final ReviewStatisticsService statisticsService;
    private final NotifierManager notifierManager;

    // ==================== 配置管理 ====================

    /**
     * 获取所有配置
     */
    @GetMapping("/list")
    public R<List<ReviewConfig>> listConfigs() {
        // 从数据库查询所有配置
        return R.ok(); // 需要实现
    }

    /**
     * 获取配置值
     */
    @GetMapping("/get")
    public R<String> getConfig(@RequestParam String key) {
        return R.ok(configService.getString(key));
    }

    /**
     * 更新配置
     */
    @PostMapping("/update")
    public R<Void> updateConfig(@RequestParam String key, @RequestParam String value) {
        configService.updateConfig(key, value);
        return R.ok();
    }

    /**
     * 批量更新配置
     */
    @PostMapping("/batch-update")
    public R<Void> batchUpdateConfig(@RequestBody Map<String, String> configs) {
        configs.forEach(configService::updateConfig);
        return R.ok();
    }

    /**
     * 刷新配置缓存
     */
    @PostMapping("/refresh")
    public R<Void> refreshCache() {
        configService.refreshCache();
        return R.ok();
    }

    // ==================== 规则管理 ====================

    /**
     * 获取所有规则
     */
    @GetMapping("/rules")
    public R<List<ReviewRule>> listRules() {
        return R.ok(configService.getEnabledRules());
    }

    /**
     * 获取指定维度的规则
     */
    @GetMapping("/rules/{dimension}")
    public R<List<ReviewRule>> getRulesByDimension(@PathVariable String dimension) {
        return R.ok(configService.getRulesByDimension(dimension));
    }

    // ==================== 项目配置管理 ====================

    /**
     * 分页查询项目配置
     */
    @GetMapping("/project/list")
    public TableDataInfo<ProjectConfig> listProjectConfigs(PageQuery pageQuery) {
        return projectConfigService.queryPageList(pageQuery);
    }

    /**
     * 查询所有项目配置
     */
    @GetMapping("/project/all")
    public R<List<ProjectConfig>> listAllProjectConfigs() {
        return R.ok(projectConfigService.listAll());
    }

    /**
     * 根据ID获取项目配置
     */
    @GetMapping("/project/{id}")
    public R<ProjectConfig> getProjectConfigById(@PathVariable Long id) {
        return R.ok(projectConfigService.getById(id));
    }

    /**
     * 根据项目名和平台获取配置
     */
    @GetMapping("/project/query")
    public R<ProjectConfig> getProjectConfig(
        @RequestParam String projectName,
        @RequestParam String platform) {
        return R.ok(configService.getProjectConfig(projectName, platform));
    }

    /**
     * 新增项目配置
     */
    @PostMapping("/project")
    public R<Void> createProjectConfig(@RequestBody ProjectConfig config) {
        projectConfigService.save(config);
        return R.ok();
    }

    /**
     * 更新项目配置
     */
    @PutMapping("/project")
    public R<Void> updateProjectConfig(@RequestBody ProjectConfig config) {
        projectConfigService.updateById(config);
        return R.ok();
    }

    /**
     * 删除项目配置
     */
    @DeleteMapping("/project/{ids}")
    public R<Void> deleteProjectConfig(@PathVariable Long[] ids) {
        projectConfigService.removeBatchByIds(Arrays.asList(ids));
        return R.ok();
    }

    /**
     * 测试通知渠道配置
     */
    @PostMapping("/project/test-notification")
    public R<Void> testNotification(@RequestBody ProjectConfig config) {
        notifierManager.testNotification(config);
        return R.ok();
    }

    // ==================== 趋势统计 ====================

    /**
     * 获取趋势数据
     */
    @GetMapping("/trend")
    public R<List<?>> getTrend(
        @RequestParam String projectName,
        @RequestParam String platform,
        @RequestParam(defaultValue = "30") int days) {
        return R.ok(statisticsService.getTrend(projectName, platform, null, days));
    }

    /**
     * 获取趋势报告
     */
    @GetMapping("/trend-report")
    public R<String> getTrendReport(
        @RequestParam String projectName,
        @RequestParam String platform,
        @RequestParam(defaultValue = "30") int days) {
        return R.ok("操作成功", statisticsService.generateTrendReport(projectName, platform, null, days));
    }

    // ==================== 评分等级 ====================

    /**
     * 获取评分等级配置
     */
    @GetMapping("/score-levels")
    public R<Map<String, Integer>> getScoreLevels() {
        return R.ok(Map.of(
            "excellent", configService.getInt("scoring.excellent_score", 90),
            "good", configService.getInt("scoring.good_score", 80),
            "pass", configService.getInt("scoring.pass_score", 70),
            "needs_improvement", configService.getInt("scoring.pass_score", 60)
        ));
    }
}
