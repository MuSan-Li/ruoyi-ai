package org.ruoyi.codereview.config;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.ruoyi.codereview.platform.GitPlatformClient;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Git 平台客户端配置
 * <p>
 * 支持两种模式：
 * 1. YAML 静态配置：启动时根据 YAML 创建客户端（兼容旧方式）
 * 2. 数据库动态配置：运行时根据项目配置动态创建客户端
 * <p>
 * 通过 @Import 由 CodeReviewAutoConfiguration 导入，无需 @Configuration 注解
 */
public class PlatformClientConfig {

    // 连接池配置常量
    private static final int MAX_CONN_TOTAL = 100;
    private static final int MAX_CONN_PER_ROUTE = 20;
    private static final int CONNECT_TIMEOUT_SECONDS = 30;
    private static final int READ_TIMEOUT_SECONDS = 120;
    private static final long CONNECTION_TTL_SECONDS = 60;

    /**
     * RestTemplate Bean（使用 Apache HttpClient 连接池）
     * <p>
     * 优化点：
     * - 连接池复用，避免每次请求创建新连接
     * - 配置最大连接数和每路由连接数
     * - 设置连接存活时间
     */
    @org.springframework.context.annotation.Bean
    public RestTemplate codereviewRestTemplate() {
        // 配置连接池
        PoolingHttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
            .setMaxConnTotal(MAX_CONN_TOTAL)
            .setMaxConnPerRoute(MAX_CONN_PER_ROUTE)
            .setValidateAfterInactivity(Timeout.ofSeconds(CONNECTION_TTL_SECONDS))
            .build();

        // 配置请求参数
        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectTimeout(Timeout.ofSeconds(CONNECT_TIMEOUT_SECONDS))
            .setResponseTimeout(Timeout.ofSeconds(READ_TIMEOUT_SECONDS))
            .setConnectionRequestTimeout(Timeout.ofSeconds(CONNECT_TIMEOUT_SECONDS))
            .build();

        // 创建 HttpClient
        CloseableHttpClient httpClient = HttpClients.custom()
            .setConnectionManager(connectionManager)
            .setDefaultRequestConfig(requestConfig)
            .evictIdleConnections(Timeout.ofSeconds(CONNECTION_TTL_SECONDS))
            .build();

        // 创建 RestTemplate
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
        return new RestTemplate(factory);
    }

    /**
     * 兼容旧方式：YAML 配置创建客户端
     * <p>
     * 仅在 YAML 中配置了平台 URL 时才创建，作为 fallback
     */
    @org.springframework.context.annotation.Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "codereview.platform.gitlab", name = "url")
    public org.ruoyi.codereview.platform.gitlab.GitLabClient gitLabClient(
        CodeReviewProperties properties, RestTemplate codereviewRestTemplate) {
        return new org.ruoyi.codereview.platform.gitlab.GitLabClient(
            properties.getPlatform().getGitlab(), codereviewRestTemplate);
    }

    @org.springframework.context.annotation.Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "codereview.platform.github", name = "url")
    public org.ruoyi.codereview.platform.github.GitHubClient gitHubClient(
        CodeReviewProperties properties, RestTemplate codereviewRestTemplate) {
        return new org.ruoyi.codereview.platform.github.GitHubClient(
            properties.getPlatform().getGithub(), codereviewRestTemplate);
    }

    @org.springframework.context.annotation.Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "codereview.platform.gitea", name = "url")
    public org.ruoyi.codereview.platform.gitea.GiteaClient giteaClient(
        CodeReviewProperties properties, RestTemplate codereviewRestTemplate) {
        return new org.ruoyi.codereview.platform.gitea.GiteaClient(
            properties.getPlatform().getGitea(), codereviewRestTemplate);
    }

    /**
     * 将所有可用的 GitPlatformClient 聚合为 Map，供 CodeReviewService 注入
     * <p>
     * 这里的 Map 仅包含 YAML 静态配置的客户端，作为 fallback
     */
    @org.springframework.context.annotation.Bean
    public Map<String, GitPlatformClient> platformClientMap(List<GitPlatformClient> clients) {
        Map<String, GitPlatformClient> map = new HashMap<>();
        for (GitPlatformClient client : clients) {
            map.put(client.getPlatform(), client);
        }
        return map;
    }
}
