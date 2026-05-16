package org.ruoyi.codereview.controller.admin;

import lombok.RequiredArgsConstructor;
import org.ruoyi.codereview.config.CodeReviewProperties;
import org.ruoyi.common.core.domain.R;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端 - 代码审查配置控制器
 */
@RestController
@RequestMapping("/admin/codereview/config")
@RequiredArgsConstructor
public class AdminCodeReviewConfigController {

    private final CodeReviewProperties properties;

    @GetMapping
    public R<CodeReviewProperties> getConfig() {
        return R.ok(properties);
    }

    @PutMapping("/llm")
    public R<Void> updateLlmConfig(@RequestBody CodeReviewProperties.LlmConfig config) {
        properties.setLlm(config);
        return R.ok();
    }

    @PutMapping("/platform")
    public R<Void> updatePlatformConfig(@RequestBody CodeReviewProperties.PlatformConfig config) {
        properties.setPlatform(config);
        return R.ok();
    }

    @PutMapping("/notify")
    public R<Void> updateNotifyConfig(@RequestBody CodeReviewProperties.NotifyConfig config) {
        properties.setNotify(config);
        return R.ok();
    }

    @PutMapping("/review")
    public R<Void> updateReviewConfig(@RequestBody CodeReviewProperties.ReviewConfig config) {
        properties.setReview(config);
        return R.ok();
    }
}
