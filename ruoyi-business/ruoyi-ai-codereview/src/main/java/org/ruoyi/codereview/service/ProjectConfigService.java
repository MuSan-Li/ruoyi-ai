package org.ruoyi.codereview.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.codereview.entity.ProjectConfig;
import org.ruoyi.codereview.mapper.ProjectConfigMapper;
import org.ruoyi.common.mybatis.core.page.PageQuery;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 项目配置服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectConfigService {

    private final ProjectConfigMapper projectConfigMapper;
    private final ReviewConfigService reviewConfigService;

    /**
     * 分页查询项目配置
     */
    public TableDataInfo<ProjectConfig> queryPageList(PageQuery pageQuery) {
        Page<ProjectConfig> page = projectConfigMapper.selectPage(
                pageQuery.build(),
                new LambdaQueryWrapper<ProjectConfig>()
                        .orderByDesc(ProjectConfig::getCreateTime)
        );
        return TableDataInfo.build(page);
    }

    /**
     * 查询所有项目配置
     */
    public List<ProjectConfig> listAll() {
        return projectConfigMapper.selectList(
                new LambdaQueryWrapper<ProjectConfig>()
                        .orderByDesc(ProjectConfig::getCreateTime)
        );
    }

    /**
     * 根据ID查询
     */
    public ProjectConfig getById(Long id) {
        return projectConfigMapper.selectById(id);
    }

    /**
     * 根据项目名和平台查询
     */
    public ProjectConfig getByProjectNameAndPlatform(String projectName, String platform) {
        return projectConfigMapper.selectOne(
                new LambdaQueryWrapper<ProjectConfig>()
                        .eq(ProjectConfig::getProjectName, projectName)
                        .eq(ProjectConfig::getPlatform, platform)
        );
    }

    /**
     * 新增项目配置
     */
    @Transactional
    public void save(ProjectConfig config) {
        // 检查是否已存在
        ProjectConfig existing = getByProjectNameAndPlatform(config.getProjectName(), config.getPlatform());
        if (existing != null) {
            throw new IllegalArgumentException("项目配置已存在: " + config.getProjectName() + " - " + config.getPlatform());
        }
        projectConfigMapper.insert(config);
        refreshCache();
        log.info("新增项目配置: {} - {}", config.getProjectName(), config.getPlatform());
    }

    /**
     * 更新项目配置
     */
    @Transactional
    public void updateById(ProjectConfig config) {
        projectConfigMapper.updateById(config);
        refreshCache();
        log.info("更新项目配置: {} - {}", config.getProjectName(), config.getPlatform());
    }

    /**
     * 批量删除项目配置
     */
    @Transactional
    public void removeBatchByIds(List<Long> ids) {
        projectConfigMapper.deleteBatchIds(ids);
        refreshCache();
        log.info("删除项目配置: {}", ids);
    }

    /**
     * 删除单个项目配置
     */
    @Transactional
    public void removeById(Long id) {
        projectConfigMapper.deleteById(id);
        refreshCache();
        log.info("删除项目配置: {}", id);
    }

    /**
     * 刷新配置缓存
     */
    private void refreshCache() {
        reviewConfigService.refreshCache();
    }
}
