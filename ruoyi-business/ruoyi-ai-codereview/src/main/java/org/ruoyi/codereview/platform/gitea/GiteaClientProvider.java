package org.ruoyi.codereview.platform.gitea;

import org.ruoyi.codereview.config.CodeReviewProperties;
import org.ruoyi.codereview.entity.dto.PlatformConfig;
import org.ruoyi.codereview.platform.GitPlatformClient;
import org.ruoyi.codereview.platform.PlatformClientProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Gitea 平台客户端提供者
 */
@Component
public class GiteaClientProvider implements PlatformClientProvider {

    @Override
    public String getPlatformType() {
        return "gitea";
    }

    @Override
    public GitPlatformClient createClient(PlatformConfig config, RestTemplate restTemplate) {
        return new GiteaClient(config.getUrl(), config.getToken(), restTemplate);
    }

    @Override
    public GitPlatformClient createClient(CodeReviewProperties properties, RestTemplate restTemplate) {
        return new GiteaClient(properties.getPlatform().getGitea(), restTemplate);
    }

    @Override
    public boolean isEnabled(CodeReviewProperties properties) {
        CodeReviewProperties.GiteaConfig config = properties.getPlatform().getGitea();
        return config != null && config.getUrl() != null && !config.getUrl().isEmpty();
    }
}
