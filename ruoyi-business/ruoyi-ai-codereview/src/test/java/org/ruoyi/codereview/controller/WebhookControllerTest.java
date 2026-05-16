package org.ruoyi.codereview.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ruoyi.codereview.config.CodeReviewProperties;
import org.ruoyi.codereview.service.CodeReviewService;
import org.ruoyi.common.core.domain.R;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * WebhookController 单元测试
 */
@Tag("dev")
@ExtendWith(MockitoExtension.class)
class WebhookControllerTest {

    @Mock
    private CodeReviewService codeReviewService;

    @Mock
    private CodeReviewProperties properties;

    @InjectMocks
    private WebhookController webhookController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(webhookController).build();
    }

    // ==================== GitLab Webhook 测试 ====================

    @Test
    @DisplayName("GitLab Webhook - MR 事件处理成功")
    void gitLabWebhook_mergeRequest_success() throws Exception {
        // 配置 mock
        CodeReviewProperties.PlatformConfig platformConfig = new CodeReviewProperties.PlatformConfig();
        CodeReviewProperties.GitLabConfig gitlabConfig = new CodeReviewProperties.GitLabConfig();
        gitlabConfig.setEnabled(true);
        gitlabConfig.setWebhookSecret("test-secret");
        platformConfig.setGitlab(gitlabConfig);
        when(properties.getPlatform()).thenReturn(platformConfig);

        String payload = """
            {
                "object_kind": "merge_request",
                "project": {"id": 1, "name": "test"},
                "object_attributes": {
                    "source_branch": "feature",
                    "target_branch": "main",
                    "url": "https://gitlab.com/test/mr/1",
                    "last_commit_id": "abc123"
                }
            }
            """;

        mockMvc.perform(post("/codereview/webhook/gitlab")
                .header("X-Gitlab-Token", "test-secret")
                .header("X-Gitlab-Event", "Merge Request")
                .contentType("application/json")
                .content(payload))
            .andExpect(status().isOk());

        verify(codeReviewService).handleGitLabMergeRequest(any());
    }

    @Test
    @DisplayName("GitLab Webhook - Push 事件处理成功")
    void gitLabWebhook_push_success() throws Exception {
        CodeReviewProperties.PlatformConfig platformConfig = new CodeReviewProperties.PlatformConfig();
        CodeReviewProperties.GitLabConfig gitlabConfig = new CodeReviewProperties.GitLabConfig();
        gitlabConfig.setEnabled(true);
        platformConfig.setGitlab(gitlabConfig);
        when(properties.getPlatform()).thenReturn(platformConfig);

        String payload = """
            {
                "object_kind": "push",
                "ref": "refs/heads/main",
                "before": "abc123",
                "after": "def456",
                "repository": {"id": 1, "name": "test"}
            }
            """;

        mockMvc.perform(post("/codereview/webhook/gitlab")
                .header("X-Gitlab-Event", "Push Hook")
                .contentType("application/json")
                .content(payload))
            .andExpect(status().isOk());

        verify(codeReviewService).handleGitLabPush(any());
    }

    @Test
    @DisplayName("GitLab Webhook - 平台禁用时返回失败")
    void gitLabWebhook_disabled_returnsFail() throws Exception {
        CodeReviewProperties.PlatformConfig platformConfig = new CodeReviewProperties.PlatformConfig();
        CodeReviewProperties.GitLabConfig gitlabConfig = new CodeReviewProperties.GitLabConfig();
        gitlabConfig.setEnabled(false);
        platformConfig.setGitlab(gitlabConfig);
        when(properties.getPlatform()).thenReturn(platformConfig);

        mockMvc.perform(post("/codereview/webhook/gitlab")
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(500));

        verify(codeReviewService, never()).handleGitLabMergeRequest(any());
    }

    @Test
    @DisplayName("GitLab Webhook - Token 验证失败")
    void gitLabWebhook_invalidToken_returnsUnauthorized() throws Exception {
        CodeReviewProperties.PlatformConfig platformConfig = new CodeReviewProperties.PlatformConfig();
        CodeReviewProperties.GitLabConfig gitlabConfig = new CodeReviewProperties.GitLabConfig();
        gitlabConfig.setEnabled(true);
        gitlabConfig.setWebhookSecret("correct-secret");
        platformConfig.setGitlab(gitlabConfig);
        when(properties.getPlatform()).thenReturn(platformConfig);

        mockMvc.perform(post("/codereview/webhook/gitlab")
                .header("X-Gitlab-Token", "wrong-secret")
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(500));

        verify(codeReviewService, never()).handleGitLabMergeRequest(any());
    }

    // ==================== GitHub Webhook 测试 ====================

    @Test
    @DisplayName("GitHub Webhook - PR 事件处理成功")
    void gitHubWebhook_pullRequest_success() throws Exception {
        CodeReviewProperties.PlatformConfig platformConfig = new CodeReviewProperties.PlatformConfig();
        CodeReviewProperties.GitHubConfig githubConfig = new CodeReviewProperties.GitHubConfig();
        githubConfig.setEnabled(true);
        platformConfig.setGithub(githubConfig);
        when(properties.getPlatform()).thenReturn(platformConfig);

        String payload = """
            {
                "action": "opened",
                "repository": {"id": 1, "name": "test"},
                "pull_request": {
                    "html_url": "https://github.com/test/pr/1",
                    "head": {"ref": "feature", "sha": "abc"},
                    "base": {"ref": "main"}
                }
            }
            """;

        mockMvc.perform(post("/codereview/webhook/github")
                .header("X-GitHub-Event", "pull_request")
                .contentType("application/json")
                .content(payload))
            .andExpect(status().isOk());

        verify(codeReviewService).handleGitHubPullRequest(any());
    }

    @Test
    @DisplayName("GitHub Webhook - Push 事件处理成功")
    void gitHubWebhook_push_success() throws Exception {
        CodeReviewProperties.PlatformConfig platformConfig = new CodeReviewProperties.PlatformConfig();
        CodeReviewProperties.GitHubConfig githubConfig = new CodeReviewProperties.GitHubConfig();
        githubConfig.setEnabled(true);
        platformConfig.setGithub(githubConfig);
        when(properties.getPlatform()).thenReturn(platformConfig);

        mockMvc.perform(post("/codereview/webhook/github")
                .header("X-GitHub-Event", "push")
                .contentType("application/json")
                .content("{\"repository\":{\"id\":1}}"))
            .andExpect(status().isOk());

        verify(codeReviewService).handleGitHubPush(any());
    }

    @Test
    @DisplayName("GitHub Webhook - 平台禁用时返回失败")
    void gitHubWebhook_disabled_returnsFail() throws Exception {
        CodeReviewProperties.PlatformConfig platformConfig = new CodeReviewProperties.PlatformConfig();
        CodeReviewProperties.GitHubConfig githubConfig = new CodeReviewProperties.GitHubConfig();
        githubConfig.setEnabled(false);
        platformConfig.setGithub(githubConfig);
        when(properties.getPlatform()).thenReturn(platformConfig);

        mockMvc.perform(post("/codereview/webhook/github")
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(500));
    }

    // ==================== Gitea Webhook 测试 ====================

    @Test
    @DisplayName("Gitea Webhook - PR 事件处理成功")
    void giteaWebhook_pullRequest_success() throws Exception {
        CodeReviewProperties.PlatformConfig platformConfig = new CodeReviewProperties.PlatformConfig();
        CodeReviewProperties.GiteaConfig giteaConfig = new CodeReviewProperties.GiteaConfig();
        giteaConfig.setEnabled(true);
        platformConfig.setGitea(giteaConfig);
        when(properties.getPlatform()).thenReturn(platformConfig);

        mockMvc.perform(post("/codereview/webhook/gitea")
                .header("X-Gitea-Event", "pull_request")
                .contentType("application/json")
                .content("{\"repository\":{\"id\":1}}"))
            .andExpect(status().isOk());

        verify(codeReviewService).handleGiteaPullRequest(any());
    }

    @Test
    @DisplayName("Gitea Webhook - Push 事件处理成功")
    void giteaWebhook_push_success() throws Exception {
        CodeReviewProperties.PlatformConfig platformConfig = new CodeReviewProperties.PlatformConfig();
        CodeReviewProperties.GiteaConfig giteaConfig = new CodeReviewProperties.GiteaConfig();
        giteaConfig.setEnabled(true);
        platformConfig.setGitea(giteaConfig);
        when(properties.getPlatform()).thenReturn(platformConfig);

        mockMvc.perform(post("/codereview/webhook/gitea")
                .header("X-Gitea-Event", "push")
                .contentType("application/json")
                .content("{\"repository\":{\"id\":1}}"))
            .andExpect(status().isOk());

        verify(codeReviewService).handleGiteaPush(any());
    }

    @Test
    @DisplayName("Gitea Webhook - Token 验证失败")
    void giteaWebhook_invalidToken_returnsUnauthorized() throws Exception {
        CodeReviewProperties.PlatformConfig platformConfig = new CodeReviewProperties.PlatformConfig();
        CodeReviewProperties.GiteaConfig giteaConfig = new CodeReviewProperties.GiteaConfig();
        giteaConfig.setEnabled(true);
        giteaConfig.setWebhookSecret("correct-secret");
        platformConfig.setGitea(giteaConfig);
        when(properties.getPlatform()).thenReturn(platformConfig);

        mockMvc.perform(post("/codereview/webhook/gitea")
                .header("X-Gitea-Token", "wrong-secret")
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(500));
    }

    // ==================== 边界情况测试 ====================

    @Test
    @DisplayName("未知事件类型 - 不处理")
    void unknownEventType_noProcessing() throws Exception {
        CodeReviewProperties.PlatformConfig platformConfig = new CodeReviewProperties.PlatformConfig();
        CodeReviewProperties.GitLabConfig gitlabConfig = new CodeReviewProperties.GitLabConfig();
        gitlabConfig.setEnabled(true);
        platformConfig.setGitlab(gitlabConfig);
        when(properties.getPlatform()).thenReturn(platformConfig);

        mockMvc.perform(post("/codereview/webhook/gitlab")
                .header("X-Gitlab-Event", "unknown_event")
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isOk());

        verify(codeReviewService, never()).handleGitLabMergeRequest(any());
        verify(codeReviewService, never()).handleGitLabPush(any());
    }

    @Test
    @DisplayName("空 Payload - 优雅处理")
    void emptyPayload_handledGracefully() throws Exception {
        CodeReviewProperties.PlatformConfig platformConfig = new CodeReviewProperties.PlatformConfig();
        CodeReviewProperties.GitLabConfig gitlabConfig = new CodeReviewProperties.GitLabConfig();
        gitlabConfig.setEnabled(true);
        platformConfig.setGitlab(gitlabConfig);
        when(properties.getPlatform()).thenReturn(platformConfig);

        mockMvc.perform(post("/codereview/webhook/gitlab")
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isOk());
    }
}
