package org.ruoyi.codereview.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.codereview.entity.MrReviewLog;
import org.ruoyi.codereview.entity.PushReviewLog;
import org.ruoyi.codereview.mapper.MrReviewLogMapper;
import org.ruoyi.codereview.mapper.PushReviewLogMapper;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.common.mybatis.core.page.PageQuery;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 审查日志管理 Controller
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/codereview/reviewLog")
public class ReviewLogController {

    private final MrReviewLogMapper mrReviewLogMapper;
    private final PushReviewLogMapper pushReviewLogMapper;

    /**
     * 分页查询 MR 审查日志
     */
    @SaCheckPermission("codereview:reviewLog:list")
    @GetMapping("/mr/list")
    public TableDataInfo<MrReviewLog> mrList(MrReviewLog query, PageQuery pageQuery) {
        LambdaQueryWrapper<MrReviewLog> lqw = new LambdaQueryWrapper<>();
        lqw.like(query.getProjectName() != null, MrReviewLog::getProjectName, query.getProjectName());
        lqw.eq(query.getPlatform() != null, MrReviewLog::getPlatform, query.getPlatform());
        lqw.eq(query.getAuthor() != null, MrReviewLog::getAuthor, query.getAuthor());
        lqw.orderByDesc(MrReviewLog::getCreateTime);
        Page<MrReviewLog> page = mrReviewLogMapper.selectPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 获取 MR 审查日志详情
     */
    @SaCheckPermission("codereview:reviewLog:query")
    @GetMapping("/mr/{id}")
    public R<MrReviewLog> getMrInfo(@PathVariable Long id) {
        return R.ok(mrReviewLogMapper.selectById(id));
    }

    /**
     * 分页查询 Push 审查日志
     */
    @SaCheckPermission("codereview:reviewLog:list")
    @GetMapping("/push/list")
    public TableDataInfo<PushReviewLog> pushList(PushReviewLog query, PageQuery pageQuery) {
        LambdaQueryWrapper<PushReviewLog> lqw = new LambdaQueryWrapper<>();
        lqw.like(query.getProjectName() != null, PushReviewLog::getProjectName, query.getProjectName());
        lqw.eq(query.getPlatform() != null, PushReviewLog::getPlatform, query.getPlatform());
        lqw.orderByDesc(PushReviewLog::getCreateTime);
        Page<PushReviewLog> page = pushReviewLogMapper.selectPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 获取 Push 审查日志详情
     */
    @SaCheckPermission("codereview:reviewLog:query")
    @GetMapping("/push/{id}")
    public R<PushReviewLog> getPushInfo(@PathVariable Long id) {
        return R.ok(pushReviewLogMapper.selectById(id));
    }

    /**
     * 删除 MR 审查日志
     */
    @SaCheckPermission("codereview:reviewLog:remove")
    @DeleteMapping("/mr/{ids}")
    public R<Void> removeMr(@PathVariable Long[] ids) {
        mrReviewLogMapper.deleteByIds(java.util.List.of(ids));
        return R.ok();
    }

    /**
     * 删除 Push 审查日志
     */
    @SaCheckPermission("codereview:reviewLog:remove")
    @DeleteMapping("/push/{ids}")
    public R<Void> removePush(@PathVariable Long[] ids) {
        pushReviewLogMapper.deleteByIds(java.util.List.of(ids));
        return R.ok();
    }
}
