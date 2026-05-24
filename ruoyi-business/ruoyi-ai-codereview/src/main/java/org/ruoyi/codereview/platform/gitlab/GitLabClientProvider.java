package org.ruoyi.codereview.platform.gitlab;

import org.ruoyi.codereview.config.CodeReviewProperties;
import org.ruoyi.codereview.entity.dto.PlatformConfig;
import org.ruoyi.codereview.platform.GitPlatformClient;
import org.ruoyi.codereview.platform.PlatformClientProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * GitLab 平台客户端提供者
 */
@Component
public class GitLabClientProvider implements PlatformClientProvider {

    @Override
    public String getPlatformType() {
        return "gitlab";
    }

    @Override
    public GitPlatformClient createClient(PlatformConfig config, RestTemplate restTemplate) {
        return new GitLabClient(config.getUrl(), config.getToken(), restTemplate);
    }

    @Override
    public GitPlatformClient createClient(CodeReviewProperties properties, RestTemplate restTemplate) {
        return new GitLabClient(properties.getPlatform().getGitlab(), restTemplate);
    }

    @Override
    public boolean isEnabled(CodeReviewProperties properties) {
        CodeReviewProperties.GitLabConfig config = properties.getPlatform().getGitlab();
        return config != null && config.getUrl() != null && !config.getUrl().isEmpty();
    }
}
