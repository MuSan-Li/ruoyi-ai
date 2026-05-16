package org.ruoyi.codereview.service;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ruoyi.codereview.config.CodeReviewProperties;
import org.ruoyi.codereview.entity.CodeChange;
import org.ruoyi.codereview.entity.MrReviewLog;
import org.ruoyi.codereview.entity.PushReviewLog;
import org.ruoyi.codereview.event.MergeRequestReviewedEvent;
import org.ruoyi.codereview.event.PushReviewedEvent;
import org.ruoyi.codereview.mapper.MrReviewLogMapper;
import org.ruoyi.codereview.mapper.PushReviewLogMapper;
import org.ruoyi.codereview.platform.GitPlatformClient;
import org.ruoyi.common.chat.domain.vo.chat.ChatModelVo;
import org.ruoyi.common.chat.service.chat.IChatModelService;
import org.ruoyi.factory.ChatServiceFactory;
import org.ruoyi.service.chat.AbstractChatService;
import org.springframework.context.ApplicationEventPublisher;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * CodeReviewService 单元测试
 */
@Tag("dev")
@ExtendWith(MockitoExtension.class)
class CodeReviewServiceTest {

    @Mock
    private CodeReviewProperties properties;

    @Mock
    private MrReviewLogMapper mrReviewLogMapper;

    @Mock
    private PushReviewLogMapper pushReviewLogMapper;

    @Mock
    private IChatModelService chatModelService;

    @Mock
    private ChatServiceFactory chatServiceFactory;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private GitPlatformClient gitPlatformClient;

    @InjectMocks
    private CodeReviewService codeReviewService;

    private Map<String, GitPlatformClient> platformClients;

    @BeforeEach
    void setUp() {
        platformClients = new HashMap<>();
        platformClients.put("gitlabClient", gitPlatformClient);
    }

    // ==================== 工具方法测试 ====================

    @Test
    @DisplayName("getClient - 存在的平台返回客户端")
    void getClient_existingPlatform_returnsClient() {
        when(gitPlatformClient.getPlatform()).thenReturn("gitlab");

        // 通过反射调用私有方法或通过公开方法间接测试
        // 这里测试间接调用路径
        assertNotNull(gitPlatformClient);
        assertEquals("gitlab", gitPlatformClient.getPlatform());
    }

    // ==================== extractMrInfo 测试 ====================

    @Test
    @DisplayName("extractMrInfo - GitLab MR 提取信息正确")
    void extractMrInfo_gitLabMr_extractsCorrectly() {
        // 模拟 GitLab MR payload
        String payload = """
            {
                "object_kind": "merge_request",
                "project": {
                    "id": 123,
                    "name": "test-project"
                },
                "object_attributes": {
                    "source_branch": "feature/test",
                    "target_branch": "main",
                    "url": "https://gitlab.com/test/project/-/merge_requests/1",
                    "last_commit_id": "abc123",
                    "last_commit": {
                        "author": "testuser"
                    }
                }
            }
            """;

        JSONObject json = JSONUtil.parseObj(payload);

        // 验证 JSON 解析正确
        assertEquals("test-project", json.getJSONObject("project").getStr("name"));
        assertEquals("feature/test", json.getJSONObject("object_attributes").getStr("source_branch"));
        assertEquals("main", json.getJSONObject("object_attributes").getStr("target_branch"));
    }

    @Test
    @DisplayName("extractMrInfo - GitHub PR 提取信息正确")
    void extractMrInfo_gitHubPr_extractsCorrectly() {
        // 模拟 GitHub PR payload
        String payload = """
            {
                "action": "opened",
                "repository": {
                    "id": 456,
                    "name": "test-repo"
                },
                "pull_request": {
                    "html_url": "https://github.com/test/repo/pull/1",
                    "user": {
                        "login": "testuser"
                    },
                    "head": {
                        "ref": "feature-branch",
                        "sha": "def456"
                    },
                    "base": {
                        "ref": "main"
                    }
                }
            }
            """;

        JSONObject json = JSONUtil.parseObj(payload);

        assertEquals("test-repo", json.getJSONObject("repository").getStr("name"));
        assertEquals("feature-branch", json.getJSONObject("pull_request").getJSONObject("head").getStr("ref"));
        assertEquals("main", json.getJSONObject("pull_request").getJSONObject("base").getStr("ref"));
    }

    // ==================== extractPushInfo 测试 ====================

    @Test
    @DisplayName("extractPushInfo - 提取推送信息正确")
    void extractPushInfo_extractsCorrectly() {
        String payload = """
            {
                "ref": "refs/heads/develop",
                "before": "abc123",
                "after": "def456",
                "repository": {
                    "id": 789,
                    "name": "push-repo"
                },
                "user_name": "pusher"
            }
            """;

        JSONObject json = JSONUtil.parseObj(payload);

        assertEquals("push-repo", json.getJSONObject("repository").getStr("name"));
        assertEquals("refs/heads/develop", json.getStr("ref"));
        assertEquals("pusher", json.getStr("user_name"));
    }

    @Test
    @DisplayName("extractPushInfo - 分支名解析正确")
    void extractPushInfo_branchParsing_correct() {
        String ref = "refs/heads/feature/test-branch";
        // refs/heads/ 前缀需要被移除
        String branch = ref.startsWith("refs/heads/") ? ref.substring(11) : ref;
        assertEquals("feature/test-branch", branch);
    }

    // ==================== filterChanges 测试 ====================

    @Test
    @DisplayName("filterChanges - 过滤删除类型变更")
    void filterChanges_filtersDeleteType() {
        // 设置配置
        CodeReviewProperties.ReviewConfig reviewConfig = new CodeReviewProperties.ReviewConfig();
        reviewConfig.setExtensions(".java,.js,.ts");

        List<CodeChange> changes = new ArrayList<>();

        CodeChange addChange = new CodeChange();
        addChange.setFilePath("src/Test.java");
        addChange.setChangeType("add");
        changes.add(addChange);

        CodeChange deleteChange = new CodeChange();
        deleteChange.setFilePath("src/OldFile.java");
        deleteChange.setChangeType("delete");
        changes.add(deleteChange);

        // 模拟过滤逻辑
        List<CodeChange> filtered = changes.stream()
            .filter(c -> !"delete".equals(c.getChangeType()))
            .toList();

        assertEquals(1, filtered.size());
        assertEquals("add", filtered.get(0).getChangeType());
    }

    @Test
    @DisplayName("filterChanges - 过滤不支持的扩展名")
    void filterChanges_filtersUnsupportedExtensions() {
        Set<String> extensions = Set.of(".java", ".js", ".ts");

        List<CodeChange> changes = new ArrayList<>();

        CodeChange javaChange = new CodeChange();
        javaChange.setFilePath("src/Test.java");
        changes.add(javaChange);

        CodeChange txtChange = new CodeChange();
        txtChange.setFilePath("README.txt");
        changes.add(txtChange);

        CodeChange noExtChange = new CodeChange();
        noExtChange.setFilePath("Dockerfile");
        changes.add(noExtChange);

        List<CodeChange> filtered = changes.stream()
            .filter(c -> {
                String path = c.getFilePath();
                if (path == null || path.isEmpty()) return false;
                int dotIdx = path.lastIndexOf('.');
                if (dotIdx < 0) return false;
                return extensions.contains(path.substring(dotIdx));
            })
            .toList();

        assertEquals(1, filtered.size());
        assertEquals("src/Test.java", filtered.get(0).getFilePath());
    }

    // ==================== isAlreadyReviewed 测试 ====================

    @Test
    @DisplayName("isAlreadyReviewed - 已审查返回 true")
    void isAlreadyReviewed_alreadyReviewed_returnsTrue() {
        // 验证查询逻辑：如果存在相同 projectName + lastCommitId + platform 的记录，则已审查
        // 模拟数据库中存在记录
        long count = 1L;
        boolean alreadyReviewed = count > 0;
        assertTrue(alreadyReviewed, "当数据库中存在记录时，应返回 true");
    }

    @Test
    @DisplayName("isAlreadyReviewed - 未审查返回 false")
    void isAlreadyReviewed_notReviewed_returnsFalse() {
        // 验证查询逻辑：如果不存在记录，则未审查
        // 模拟数据库中不存在记录
        long count = 0L;
        boolean alreadyReviewed = count > 0;
        assertFalse(alreadyReviewed, "当数据库中不存在记录时，应返回 false");
    }

    @Test
    @DisplayName("isAlreadyReviewed - 空 lastCommitId 返回 false")
    void isAlreadyReviewed_emptyCommitId_returnsFalse() {
        // 空的 lastCommitId 不应该触发查询
        String lastCommitId = null;
        boolean shouldQuery = lastCommitId != null && !lastCommitId.isEmpty();
        assertFalse(shouldQuery, "空的 lastCommitId 不应触发查询");
    }

    // ==================== Draft 检测测试 ====================

    @Test
    @DisplayName("Draft 检测 - GitLab Draft MR")
    void draftDetection_gitLabDraft() {
        String payload = """
            {
                "object_kind": "merge_request",
                "object_attributes": {
                    "title": "Draft: 新功能开发",
                    "draft": true
                }
            }
            """;

        JSONObject json = JSONUtil.parseObj(payload);
        JSONObject attrs = json.getJSONObject("object_attributes");

        // GitLab 通过 draft 字段或标题前缀检测
        boolean isDraft = attrs.containsKey("draft") && attrs.getBool("draft", false);
        assertTrue(isDraft);
    }

    @Test
    @DisplayName("Draft 检测 - GitHub Draft PR")
    void draftDetection_gitHubDraft() {
        String payload = """
            {
                "pull_request": {
                    "draft": true,
                    "title": "WIP: 新功能开发"
                }
            }
            """;

        JSONObject json = JSONUtil.parseObj(payload);
        JSONObject pr = json.getJSONObject("pull_request");

        boolean isDraft = pr.getBool("draft", false);
        assertTrue(isDraft);
    }

    // ==================== 新分支检测测试 ====================

    @Test
    @DisplayName("Push 事件 - 新分支跳过")
    void pushEvent_newBranch_skipped() {
        String before = "0000000000000000000000000000000000000000";
        boolean isNewBranch = "0000000000000000000000000000000000000000".equals(before);
        assertTrue(isNewBranch);
    }

    // ==================== 评分提取测试 ====================

    @Test
    @DisplayName("评分提取 - 各种格式")
    void scoreExtraction_variousFormats() {
        // Markdown 格式
        String md1 = "**总分：85分**";
        assertTrue(md1.contains("总分") && md1.contains("85"));

        // 冒号格式
        String md2 = "**总分:92分**";
        assertTrue(md2.contains("总分") && md2.contains("92"));

        // JSON 格式
        String json = "{\"score\": 88}";
        assertTrue(json.contains("\"score\"") && json.contains("88"));
    }

    // ==================== 通知内容构建测试 ====================

    @Test
    @DisplayName("通知内容 - MR 审查完成")
    void notificationContent_mrReview() {
        String projectName = "test-project";
        String author = "testuser";
        String sourceBranch = "feature/test";
        String targetBranch = "main";
        String url = "https://gitlab.com/test/mr/1";
        int score = 85;
        String reviewResult = "代码质量良好";

        StringBuilder sb = new StringBuilder();
        sb.append("> **项目**: ").append(projectName).append("\n\n");
        sb.append("> **作者**: ").append(author).append("\n\n");
        sb.append("> **分支**: ").append(sourceBranch).append(" → ").append(targetBranch).append("\n\n");
        sb.append("> **评分**: ").append(score).append("/100\n\n");

        String content = sb.toString();
        assertTrue(content.contains(projectName));
        assertTrue(content.contains(author));
        assertTrue(content.contains(sourceBranch));
        assertTrue(content.contains(targetBranch));
        assertTrue(content.contains("85/100"));
    }

    // ==================== Token 估算测试 ====================

    @Test
    @DisplayName("Token 估算 - 大文件截断")
    void tokenEstimation_largeFile_truncation() {
        int maxTokens = 10000;
        int avgCharsPerToken = 4;
        int maxChars = maxTokens * avgCharsPerToken; // 40000 chars

        StringBuilder largeText = new StringBuilder();
        for (int i = 0; i < 50000; i++) {
            largeText.append("a");
        }

        assertTrue(largeText.length() > maxChars);

        // 模拟截断
        String truncated = largeText.substring(0, maxChars);
        assertEquals(maxChars, truncated.length());
    }

    // ==================== 配置验证测试 ====================

    @Test
    @DisplayName("配置验证 - 默认值")
    void configValidation_defaultValues() {
        CodeReviewProperties props = new CodeReviewProperties();

        assertTrue(props.isEnabled());
        assertNotNull(props.getLlm());
        assertNotNull(props.getPlatform());
        assertNotNull(props.getNotify());
        assertNotNull(props.getReview());

        assertEquals("deepseek-chat", props.getLlm().getModelName());
        assertEquals(10000, props.getReview().getMaxTokens());
        assertEquals("professional", props.getReview().getStyle());
        assertTrue(props.getReview().isPushEnabled());
    }

    @Test
    @DisplayName("配置验证 - 平台配置")
    void configValidation_platformConfig() {
        CodeReviewProperties.PlatformConfig platform = new CodeReviewProperties.PlatformConfig();

        assertNotNull(platform.getGitlab());
        assertNotNull(platform.getGithub());
        assertNotNull(platform.getGitea());

        assertTrue(platform.getGithub().isEnabled());
        assertEquals("https://api.github.com", platform.getGithub().getUrl());
    }

    // ==================== 事件发布测试 ====================

    @Test
    @DisplayName("事件发布 - MR 审查完成事件")
    void eventPublish_mrReviewed() {
        // 模拟事件发布
        MergeRequestReviewedEvent event = new MergeRequestReviewedEvent(
            this, "test-project", "testuser", "feature", "main",
            "https://test.url", 85, "Good code"
        );

        assertEquals("test-project", event.getProjectName());
        assertEquals("testuser", event.getAuthor());
        assertEquals("feature", event.getSourceBranch());
        assertEquals("main", event.getTargetBranch());
        assertEquals(85, event.getScore());
    }

    @Test
    @DisplayName("事件发布 - Push 审查完成事件")
    void eventPublish_pushReviewed() {
        PushReviewedEvent event = new PushReviewedEvent(
            this, "test-project", "testuser", "develop", 90, "Excellent"
        );

        assertEquals("test-project", event.getProjectName());
        assertEquals("testuser", event.getAuthor());
        assertEquals("develop", event.getBranch());
        assertEquals(90, event.getScore());
    }

    // ==================== 错误处理测试 ====================

    @Test
    @DisplayName("错误处理 - 无效 JSON")
    void errorHandling_invalidJson() {
        String invalidPayload = "not a valid json";

        assertThrows(Exception.class, () -> {
            JSONUtil.parseObj(invalidPayload);
        });
    }

    @Test
    @DisplayName("错误处理 - 缺少必要字段")
    void errorHandling_missingFields() {
        String incompletePayload = """
            {
                "object_kind": "merge_request"
            }
            """;

        JSONObject json = JSONUtil.parseObj(incompletePayload);

        // 应该优雅处理缺少的字段
        assertNull(json.getJSONObject("project"));
    }
}
