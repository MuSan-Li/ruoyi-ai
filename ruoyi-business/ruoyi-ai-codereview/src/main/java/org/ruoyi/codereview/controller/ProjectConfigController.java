package org.ruoyi.codereview.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.codereview.entity.ProjectConfig;
import org.ruoyi.codereview.mapper.ProjectConfigMapper;
import org.ruoyi.codereview.notifier.DingTalkNotifier;
import org.ruoyi.codereview.notifier.FeishuNotifier;
import org.ruoyi.codereview.notifier.Notifier;
import org.ruoyi.codereview.notifier.WeComNotifier;
import org.ruoyi.codereview.notifier.WebhookNotifier;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.common.core.validate.AddGroup;
import org.ruoyi.common.core.validate.EditGroup;
import org.ruoyi.common.mybatis.core.page.PageQuery;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.springframework.http.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * 项目配置管理 Controller
 * <p>
 * 提供项目审查配置的 CRUD 接口
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/codereview/projectConfig")
public class ProjectConfigController {

    private final ProjectConfigMapper projectConfigMapper;

    /**
     * 分页查询项目配置列表
     */
    @SaCheckPermission("codereview:projectConfig:list")
    @GetMapping("/list")
    public TableDataInfo<ProjectConfig> list(ProjectConfig projectConfig, PageQuery pageQuery) {
        LambdaQueryWrapper<ProjectConfig> lqw = new LambdaQueryWrapper<>();
        lqw.like(projectConfig.getProjectName() != null, ProjectConfig::getProjectName, projectConfig.getProjectName());
        lqw.eq(projectConfig.getPlatform() != null, ProjectConfig::getPlatform, projectConfig.getPlatform());
        lqw.orderByDesc(ProjectConfig::getCreateTime);
        Page<ProjectConfig> page = projectConfigMapper.selectPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 获取所有项目配置（不分页）
     */
    @SaCheckPermission("codereview:projectConfig:list")
    @GetMapping("/listAll")
    public R<List<ProjectConfig>> listAll() {
        List<ProjectConfig> list = projectConfigMapper.selectList(
                new LambdaQueryWrapper<ProjectConfig>().orderByDesc(ProjectConfig::getCreateTime));
        return R.ok(list);
    }

    /**
     * 根据ID获取项目配置详情
     */
    @SaCheckPermission("codereview:projectConfig:query")
    @GetMapping("/{id}")
    public R<ProjectConfig> getInfo(@PathVariable Long id) {
        ProjectConfig config = projectConfigMapper.selectById(id);
        if (config == null) {
            return R.fail("项目配置不存在");
        }
        return R.ok(config);
    }

    /**
     * 根据项目名和平台获取配置
     */
    @SaCheckPermission("codereview:projectConfig:query")
    @GetMapping("/getByProject")
    public R<ProjectConfig> getByProject(@RequestParam String projectName, @RequestParam String platform) {
        ProjectConfig config = projectConfigMapper.selectOne(
                new LambdaQueryWrapper<ProjectConfig>()
                        .eq(ProjectConfig::getProjectName, projectName)
                        .eq(ProjectConfig::getPlatform, platform));
        return R.ok(config);
    }

    /**
     * 新增项目配置
     */
    @SaCheckPermission("codereview:projectConfig:add")
    @PostMapping
    public R<Void> add(@Validated(AddGroup.class) @RequestBody ProjectConfig projectConfig) {
        // 检查是否已存在
        Long count = projectConfigMapper.selectCount(
                new LambdaQueryWrapper<ProjectConfig>()
                        .eq(ProjectConfig::getProjectName, projectConfig.getProjectName())
                        .eq(ProjectConfig::getPlatform, projectConfig.getPlatform()));
        if (count > 0) {
            return R.fail("该项目配置已存在");
        }

        int result = projectConfigMapper.insert(projectConfig);
        return result > 0 ? R.ok() : R.fail("新增项目配置失败");
    }

    /**
     * 修改项目配置
     */
    @SaCheckPermission("codereview:projectConfig:edit")
    @PutMapping
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody ProjectConfig projectConfig) {
        if (projectConfig.getId() == null) {
            return R.fail("ID不能为空");
        }

        int result = projectConfigMapper.updateById(projectConfig);
        return result > 0 ? R.ok() : R.fail("修改项目配置失败");
    }

    /**
     * 删除项目配置
     */
    @SaCheckPermission("codereview:projectConfig:remove")
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        int result = projectConfigMapper.deleteByIds(List.of(ids));
        return result > 0 ? R.ok() : R.fail("删除项目配置失败");
    }

    /**
     * 启用/禁用项目审查
     */
    @SaCheckPermission("codereview:projectConfig:edit")
    @PutMapping("/changeStatus")
    public R<Void> changeStatus(@RequestBody ProjectConfig projectConfig) {
        if (projectConfig.getId() == null || projectConfig.getReviewEnabled() == null) {
            return R.fail("参数不完整");
        }

        ProjectConfig update = new ProjectConfig();
        update.setId(projectConfig.getId());
        update.setReviewEnabled(projectConfig.getReviewEnabled());

        int result = projectConfigMapper.updateById(update);
        return result > 0 ? R.ok() : R.fail("修改状态失败");
    }

    /**
     * 测试平台连接
     */
    @SaCheckPermission("codereview:projectConfig:edit")
    @PostMapping("/testConnection")
    public R<Boolean> testConnection(@RequestBody ProjectConfig projectConfig) {
        if (projectConfig.getPlatform() == null || projectConfig.getPlatformUrl() == null) {
            return R.fail("请先填写平台和平台地址");
        }

        String platform = projectConfig.getPlatform().toLowerCase();
        String url = projectConfig.getPlatformUrl().replaceAll("/+$", "");
        String token = projectConfig.getPlatformToken();
        if (token == null || token.isEmpty()) {
            return R.fail("请先填写平台Token");
        }

        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            String testUrl;

            switch (platform) {
                case "github" -> {
                    testUrl = url + "/user";
                    headers.set("Authorization", "token " + token);
                    headers.set("Accept", "application/vnd.github.v3+json");
                }
                case "gitlab" -> {
                    testUrl = url + "/api/v4/user";
                    headers.set("PRIVATE-TOKEN", token);
                }
                case "gitea" -> {
                    testUrl = url + "/api/v1/user";
                    headers.set("Authorization", "token " + token);
                }
                default -> {
                    return R.fail("不支持的平台: " + platform);
                }
            }

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(testUrl, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("平台连接测试成功: platform={}, url={}", platform, url);
                return R.ok("连接测试成功", true);
            } else {
                return R.fail("连接测试失败，HTTP状态码: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.warn("平台连接测试失败: platform={}, url={}, error={}", platform, url, e.getMessage());
            String msg = e.getMessage();
            if (msg != null && msg.length() > 200) {
                msg = msg.substring(0, 200);
            }
            return R.fail("连接测试失败: " + msg);
        }
    }

    /**
     * 发送测试通知
     */
    @SaCheckPermission("codereview:projectConfig:edit")
    @PostMapping("/testNotification")
    public R<Boolean> testNotification(@RequestBody ProjectConfig projectConfig) {
        if (projectConfig.getNotificationChannels() == null || projectConfig.getNotificationChannels().isEmpty()) {
            return R.fail("未配置通知渠道");
        }

        try {
            JSONArray channels = JSONUtil.parseArray(projectConfig.getNotificationChannels());
            boolean success = false;

            for (int i = 0; i < channels.size(); i++) {
                JSONObject channel = channels.getJSONObject(i);
                String type = channel.getStr("type");
                String webhookUrl = channel.getStr("webhookUrl");
                Boolean enabled = channel.getBool("enabled", true);

                if (!enabled || webhookUrl == null || webhookUrl.isEmpty()) {
                    continue;
                }

                // 直接创建通知器实例
                Notifier notifier = null;
                switch (type.toLowerCase()) {
                    case "wecom" -> notifier = new WeComNotifier(webhookUrl);
                    case "dingtalk" -> notifier = new DingTalkNotifier(webhookUrl,
                            channel.getBool("secretEnabled", false),
                            channel.getStr("secret"));
                    case "feishu" -> notifier = new FeishuNotifier(webhookUrl);
                    case "webhook" -> notifier = new WebhookNotifier(webhookUrl);
                }

                if (notifier != null) {
                    String title = "🧪 AI代码审查通知测试";
                    String content = "**项目**: " + projectConfig.getProjectName() + "\n" +
                            "**平台**: " + projectConfig.getPlatform() + "\n\n" +
                            "这是一条测试消息，验证通知渠道配置正确。\n\n" +
                            "✅ 如果您看到此消息，说明通知配置成功！";

                    if (notifier.send(title, content)) {
                        success = true;
                        log.info("通知渠道 {} 测试成功", type);
                    }
                }
            }

            if (success) {
                return R.ok("通知发送成功", true);
            } else {
                return R.fail("所有通知渠道发送失败");
            }
        } catch (Exception e) {
            log.error("发送测试通知失败", e);
            return R.fail("发送测试通知失败: " + e.getMessage());
        }
    }
}
