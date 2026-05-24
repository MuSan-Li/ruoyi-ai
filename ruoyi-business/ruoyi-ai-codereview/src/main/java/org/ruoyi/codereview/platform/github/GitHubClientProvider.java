package org.ruoyi.codereview.platform.github;

import org.ruoyi.codereview.config.CodeReviewProperties;
import org.ruoyi.codereview.entity.dto.PlatformConfig;
import org.ruoyi.codereview.platform.GitPlatformClient;
import org.ruoyi.codereview.platform.PlatformClientProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * GitHub 平台客户端提供者
 */
@Component
public class GitHubClientProvider implements PlatformClientProvider {

    @Override
    public String getPlatformType() {
        return "github";
    }

    @Override
    public GitPlatformClient createClient(PlatformConfig config, RestTemplate restTemplate) {
        return new GitHubClient(config.getUrl(), config.getToken(), restTemplate);
    }

    @Override
    public GitPlatformClient createClient(CodeReviewProperties properties, RestTemplate restTemplate) {
        return new GitHubClient(properties.getPlatform().getGithub(), restTemplate);
    }

    @Override
    public boolean isEnabled(CodeReviewProperties properties) {
        CodeReviewProperties.GitHubConfig config = properties.getPlatform().getGithub();
        return config != null && config.getUrl() != null && !config.getUrl().isEmpty();
    }
}
