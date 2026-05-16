package org.ruoyi.codereview.config;

import org.ruoyi.codereview.platform.github.GitHubClient;
import org.ruoyi.codereview.platform.gitlab.GitLabClient;
import org.ruoyi.codereview.platform.gitea.GiteaClient;
import org.ruoyi.codereview.platform.GitPlatformClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Git 平台客户端配置
 * <p>
 * 通过 @Import 由 CodeReviewAutoConfiguration 导入，无需 @Configuration 注解
 */
public class PlatformClientConfig {

    @Bean
    public RestTemplate codereviewRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(30_000);
        factory.setReadTimeout(120_000);
        return new RestTemplate(factory);
    }

    @Bean
    @ConditionalOnProperty(prefix = "codereview.platform.gitlab", name = "url")
    public GitLabClient gitLabClient(CodeReviewProperties properties, RestTemplate codereviewRestTemplate) {
        return new GitLabClient(properties.getPlatform().getGitlab(), codereviewRestTemplate);
    }

    @Bean
    @ConditionalOnProperty(prefix = "codereview.platform.github", name = "url")
    public GitHubClient gitHubClient(CodeReviewProperties properties, RestTemplate codereviewRestTemplate) {
        return new GitHubClient(properties.getPlatform().getGithub(), codereviewRestTemplate);
    }

    @Bean
    @ConditionalOnProperty(prefix = "codereview.platform.gitea", name = "url")
    public GiteaClient giteaClient(CodeReviewProperties properties, RestTemplate codereviewRestTemplate) {
        return new GiteaClient(properties.getPlatform().getGitea(), codereviewRestTemplate);
    }

    /**
     * 将所有可用的 GitPlatformClient 聚合为 Map，供 CodeReviewService 注入
     */
    @Bean
    public Map<String, GitPlatformClient> platformClientMap(List<GitPlatformClient> clients) {
        Map<String, GitPlatformClient> map = new HashMap<>();
        for (GitPlatformClient client : clients) {
            map.put(client.getPlatform(), client);
        }
        return map;
    }
}
