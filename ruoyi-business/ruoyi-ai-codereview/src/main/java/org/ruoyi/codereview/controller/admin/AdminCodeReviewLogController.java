package org.ruoyi.codereview.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.ruoyi.codereview.entity.MrReviewLog;
import org.ruoyi.codereview.entity.PushReviewLog;
import org.ruoyi.codereview.entity.vo.MrReviewLogVo;
import org.ruoyi.codereview.entity.vo.PushReviewLogVo;
import org.ruoyi.codereview.mapper.MrReviewLogMapper;
import org.ruoyi.codereview.mapper.PushReviewLogMapper;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.common.mybatis.core.page.PageQuery;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 管理端 - 代码审查记录控制器
 */
@RestController
@RequestMapping("/admin/codereview/log")
@RequiredArgsConstructor
public class AdminCodeReviewLogController {

    private final MrReviewLogMapper mrReviewLogMapper;
    private final PushReviewLogMapper pushReviewLogMapper;

    @GetMapping("/mr/list")
    public TableDataInfo<MrReviewLogVo> getMrReviewLogList(
            PageQuery pageQuery,
            @RequestParam(required = false) String projectName,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) LocalDateTime startDate,
            @RequestParam(required = false) LocalDateTime endDate) {

        LambdaQueryWrapper<MrReviewLog> queryWrapper = buildMrQuery(projectName, author, platform, startDate, endDate);
        Page<MrReviewLogVo> page = mrReviewLogMapper.selectVoPage(pageQuery.build(), queryWrapper);
        return TableDataInfo.build(page);
    }

    @GetMapping("/push/list")
    public TableDataInfo<PushReviewLogVo> getPushReviewLogList(
            PageQuery pageQuery,
            @RequestParam(required = false) String projectName,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) LocalDateTime startDate,
            @RequestParam(required = false) LocalDateTime endDate) {

        LambdaQueryWrapper<PushReviewLog> queryWrapper = buildPushQuery(projectName, author, platform, startDate, endDate);
        Page<PushReviewLogVo> page = pushReviewLogMapper.selectVoPage(pageQuery.build(), queryWrapper);
        return TableDataInfo.build(page);
    }

    @GetMapping("/statistics")
    public R<Map<String, Object>> getStatistics(
            @RequestParam(required = false) LocalDateTime startDate,
            @RequestParam(required = false) LocalDateTime endDate) {

        if (startDate == null) startDate = LocalDateTime.now().minusDays(30);
        if (endDate == null) endDate = LocalDateTime.now();

        Long mrTotal = mrReviewLogMapper.selectCount(
            new LambdaQueryWrapper<MrReviewLog>()
                .ge(MrReviewLog::getUpdateTime, startDate)
                .le(MrReviewLog::getUpdateTime, endDate));

        Long pushTotal = pushReviewLogMapper.selectCount(
            new LambdaQueryWrapper<PushReviewLog>()
                .ge(PushReviewLog::getUpdateTime, startDate)
                .le(PushReviewLog::getUpdateTime, endDate));

        // 使用 SQL 聚合计算平均分
        Double mrAvgScore = mrReviewLogMapper.selectAvgScore(startDate, endDate);
        Double pushAvgScore = pushReviewLogMapper.selectAvgScore(startDate, endDate);
        if (mrAvgScore == null) mrAvgScore = 0.0;
        if (pushAvgScore == null) pushAvgScore = 0.0;

        long totalCount = mrTotal + pushTotal;
        int avgScore = totalCount > 0 ? (int) ((mrAvgScore * mrTotal + pushAvgScore * pushTotal) / totalCount) : 0;

        Map<String, Object> result = new HashMap<>();
        result.put("mrTotal", mrTotal);
        result.put("pushTotal", pushTotal);
        result.put("avgScore", avgScore);
        result.put("startDate", startDate);
        result.put("endDate", endDate);
        return R.ok(result);
    }

    @GetMapping("/statistics/projects")
    public R<List<Map<String, Object>>> getProjectStatistics(
            @RequestParam(required = false) LocalDateTime startDate,
            @RequestParam(required = false) LocalDateTime endDate) {

        if (startDate == null) startDate = LocalDateTime.now().minusDays(30);
        if (endDate == null) endDate = LocalDateTime.now();

        List<MrReviewLog> mrLogs = mrReviewLogMapper.selectList(
            new LambdaQueryWrapper<MrReviewLog>()
                .ge(MrReviewLog::getUpdateTime, startDate)
                .le(MrReviewLog::getUpdateTime, endDate));

        List<PushReviewLog> pushLogs = pushReviewLogMapper.selectList(
            new LambdaQueryWrapper<PushReviewLog>()
                .ge(PushReviewLog::getUpdateTime, startDate)
                .le(PushReviewLog::getUpdateTime, endDate));

        Map<String, Map<String, Object>> projectMap = new LinkedHashMap<>();

        for (MrReviewLog log : mrLogs) {
            Map<String, Object> stats = projectMap.computeIfAbsent(log.getProjectName(), k -> newProjectStats(k));
            stats.put("mrCount", (Integer) stats.get("mrCount") + 1);
            if (log.getScore() != null) {
                stats.put("totalScore", (Integer) stats.get("totalScore") + log.getScore());
                stats.put("totalCount", (Integer) stats.get("totalCount") + 1);
            }
            if (log.getAdditions() != null) stats.put("additions", (Integer) stats.get("additions") + log.getAdditions());
            if (log.getDeletions() != null) stats.put("deletions", (Integer) stats.get("deletions") + log.getDeletions());
        }

        for (PushReviewLog log : pushLogs) {
            Map<String, Object> stats = projectMap.computeIfAbsent(log.getProjectName(), k -> newProjectStats(k));
            stats.put("pushCount", (Integer) stats.get("pushCount") + 1);
            if (log.getScore() != null) {
                stats.put("totalScore", (Integer) stats.get("totalScore") + log.getScore());
                stats.put("totalCount", (Integer) stats.get("totalCount") + 1);
            }
            if (log.getAdditions() != null) stats.put("additions", (Integer) stats.get("additions") + log.getAdditions());
            if (log.getDeletions() != null) stats.put("deletions", (Integer) stats.get("deletions") + log.getDeletions());
        }

        for (Map<String, Object> stats : projectMap.values()) {
            int count = (Integer) stats.get("totalCount");
            stats.put("avgScore", count > 0 ? (Integer) stats.get("totalScore") / count : 0);
        }

        return R.ok(new ArrayList<>(projectMap.values()));
    }

    // ==================== 辅助方法 ====================

    private Map<String, Object> newProjectStats(String name) {
        Map<String, Object> m = new HashMap<>();
        m.put("projectName", name);
        m.put("mrCount", 0);
        m.put("pushCount", 0);
        m.put("totalScore", 0);
        m.put("totalCount", 0);
        m.put("additions", 0);
        m.put("deletions", 0);
        return m;
    }

    private LambdaQueryWrapper<MrReviewLog> buildMrQuery(String projectName, String author, String platform,
                                                          LocalDateTime startDate, LocalDateTime endDate) {
        LambdaQueryWrapper<MrReviewLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.isNotBlank(projectName), MrReviewLog::getProjectName, projectName);
        wrapper.eq(StringUtils.isNotBlank(author), MrReviewLog::getAuthor, author);
        wrapper.eq(StringUtils.isNotBlank(platform), MrReviewLog::getPlatform, platform);
        wrapper.ge(startDate != null, MrReviewLog::getUpdateTime, startDate);
        wrapper.le(endDate != null, MrReviewLog::getUpdateTime, endDate);
        wrapper.orderByDesc(MrReviewLog::getUpdateTime);
        return wrapper;
    }

    private LambdaQueryWrapper<PushReviewLog> buildPushQuery(String projectName, String author, String platform,
                                                              LocalDateTime startDate, LocalDateTime endDate) {
        LambdaQueryWrapper<PushReviewLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.isNotBlank(projectName), PushReviewLog::getProjectName, projectName);
        wrapper.eq(StringUtils.isNotBlank(author), PushReviewLog::getAuthor, author);
        wrapper.eq(StringUtils.isNotBlank(platform), PushReviewLog::getPlatform, platform);
        wrapper.ge(startDate != null, PushReviewLog::getUpdateTime, startDate);
        wrapper.le(endDate != null, PushReviewLog::getUpdateTime, endDate);
        wrapper.orderByDesc(PushReviewLog::getUpdateTime);
        return wrapper;
    }
}
