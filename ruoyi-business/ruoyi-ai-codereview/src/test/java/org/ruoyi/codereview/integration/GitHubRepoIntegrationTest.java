package org.ruoyi.codereview.integration;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.ruoyi.codereview.entity.CodeChange;
import org.ruoyi.codereview.util.PromptTemplate;
import org.ruoyi.codereview.util.TokenCounter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 仓库集成测试 - 模拟 GitHub Webhook 场景
 * 测试仓库: https://github.com/MuSan-Li/ruoyi-ai.git
 */
@Tag("dev")
class GitHubRepoIntegrationTest {

    /**
     * 模拟 GitHub PR Webhook Payload
     */
    @Test
    @DisplayName("模拟 GitHub PR Webhook - 解析和验证")
    void simulateGitHubPullRequestWebhook() {
        // 模拟 GitHub PR webhook payload (基于 ruoyi-ai 仓库结构)
        String payload = """
            {
              "action": "opened",
              "number": 1,
              "repository": {
                "id": 123456789,
                "name": "ruoyi-ai",
                "full_name": "MuSan-Li/ruoyi-ai",
                "owner": {
                  "login": "MuSan-Li"
                }
              },
              "pull_request": {
                "number": 1,
                "html_url": "https://github.com/MuSan-Li/ruoyi-ai/pull/1",
                "title": "feat: 添加 AI 代码审查功能",
                "user": {
                  "login": "developer"
                },
                "head": {
                  "ref": "feature/ai-code-review",
                  "sha": "abc123def456"
                },
                "base": {
                  "ref": "main"
                },
                "draft": false
              }
            }
            """;

        JSONObject json = JSONUtil.parseObj(payload);

        // 验证解析
        assertEquals("opened", json.getStr("action"));
        assertEquals("ruoyi-ai", json.getJSONObject("repository").getStr("name"));
        assertEquals("MuSan-Li/ruoyi-ai", json.getJSONObject("repository").getStr("full_name"));
        assertEquals(1, json.getJSONObject("pull_request").getInt("number"));

        // 验证 Draft 检测
        boolean isDraft = json.getJSONObject("pull_request").getBool("draft", false);
        assertFalse(isDraft, "这不是 Draft PR，应该进行审查");

        // 验证分支信息
        String sourceBranch = json.getJSONObject("pull_request").getJSONObject("head").getStr("ref");
        String targetBranch = json.getJSONObject("pull_request").getJSONObject("base").getStr("ref");
        assertEquals("feature/ai-code-review", sourceBranch);
        assertEquals("main", targetBranch);

        System.out.println("✅ GitHub PR Webhook 解析成功");
        System.out.println("   仓库: " + json.getJSONObject("repository").getStr("full_name"));
        System.out.println("   PR: #" + json.getJSONObject("pull_request").getInt("number"));
        System.out.println("   分支: " + sourceBranch + " -> " + targetBranch);
    }

    /**
     * 模拟 GitHub Push Webhook Payload
     */
    @Test
    @DisplayName("模拟 GitHub Push Webhook - 解析和验证")
    void simulateGitHubPushWebhook() {
        String payload = """
            {
              "ref": "refs/heads/main",
              "before": "abc123def456",
              "after": "fed789cba321",
              "repository": {
                "id": 123456789,
                "name": "ruoyi-ai",
                "full_name": "MuSan-Li/ruoyi-ai"
              },
              "sender": {
                "login": "developer"
              },
              "commits": [
                {
                  "id": "fed789cba321",
                  "message": "feat: 添加代码审查模块",
                  "author": {
                    "name": "developer"
                  }
                },
                {
                  "id": "abc456def789",
                  "message": "fix: 修复空指针问题",
                  "author": {
                    "name": "developer"
                  }
                }
              ]
            }
            """;

        JSONObject json = JSONUtil.parseObj(payload);

        // 验证解析
        assertEquals("refs/heads/main", json.getStr("ref"));
        assertEquals("ruoyi-ai", json.getJSONObject("repository").getStr("name"));

        // 解析分支名
        String ref = json.getStr("ref");
        String branch = ref.startsWith("refs/heads/") ? ref.substring(11) : ref;
        assertEquals("main", branch);

        // 验证 commits
        var commits = json.getJSONArray("commits");
        assertEquals(2, commits.size());

        System.out.println("✅ GitHub Push Webhook 解析成功");
        System.out.println("   分支: " + branch);
        System.out.println("   提交数: " + commits.size());
    }

    /**
     * 模拟代码变更 - 基于仓库实际结构
     */
    @Test
    @DisplayName("模拟代码变更审查 - ruoyi-ai-codereview 模块")
    void simulateCodeChangesReview() {
        // 模拟 ruoyi-ai-codereview 模块的代码变更
        List<CodeChange> changes = new ArrayList<>();

        // 模拟新增的服务类
        CodeChange serviceChange = new CodeChange();
        serviceChange.setFilePath("ruoyi-business/ruoyi-ai-codereview/src/main/java/org/ruoyi/codereview/service/CodeReviewService.java");
        serviceChange.setFileName("CodeReviewService.java");
        serviceChange.setChangeType("modify");
        serviceChange.setAdditions(50);
        serviceChange.setDeletions(5);
        serviceChange.setDiff("""
            @@ -1,5 +1,55 @@
            +package org.ruoyi.codereview.service;
            +
            +import lombok.RequiredArgsConstructor;
            +import lombok.extern.slf4j.Slf4j;
            +
            +@Slf4j
            +@Service
            +@RequiredArgsConstructor
            +public class CodeReviewService {
            +
            +    public void handleGitLabMergeRequest(String payload) {
            +        log.info("处理 GitLab Merge Request");
            +        // 处理逻辑
            +    }
            +}
            """);
        changes.add(serviceChange);

        // 模拟新增的配置类
        CodeChange configChange = new CodeChange();
        configChange.setFilePath("ruoyi-business/ruoyi-ai-codereview/src/main/java/org/ruoyi/codereview/config/CodeReviewProperties.java");
        configChange.setFileName("CodeReviewProperties.java");
        configChange.setChangeType("add");
        configChange.setAdditions(30);
        configChange.setDeletions(0);
        configChange.setDiff("""
            +@Data
            +@ConfigurationProperties(prefix = "codereview")
            +public class CodeReviewProperties {
            +    private boolean enabled = true;
            +    private LlmConfig llm = new LlmConfig();
            +}
            """);
        changes.add(configChange);

        // 验证变更过滤
        Set<String> extensions = Set.of(".java", ".xml", ".yml", ".properties");
        List<CodeChange> filtered = changes.stream()
            .filter(c -> {
                String path = c.getFilePath();
                if (path == null) return false;
                int dotIdx = path.lastIndexOf('.');
                return dotIdx >= 0 && extensions.contains(path.substring(dotIdx));
            })
            .toList();

        assertEquals(2, filtered.size(), "应该有 2 个 Java 文件需要审查");

        // 生成审查 Prompt
        String commitMessages = "- feat: 添加代码审查模块\n- fix: 修复空指针问题";
        String userPrompt = PromptTemplate.buildUserPrompt(changes, commitMessages);

        assertTrue(userPrompt.contains("CodeReviewService.java"));
        assertTrue(userPrompt.contains("CodeReviewProperties.java"));
        assertTrue(userPrompt.contains("+50"));
        assertTrue(userPrompt.contains("feat: 添加代码审查模块"));

        // 计算 Token
        int tokens = TokenCounter.estimateTokens(userPrompt);
        assertTrue(tokens > 0);

        System.out.println("✅ 代码变更审查模拟成功");
        System.out.println("   变更文件数: " + changes.size());
        System.out.println("   过滤后文件数: " + filtered.size());
        System.out.println("   估算 Token: " + tokens);
    }

    /**
     * 测试 Prompt 生成
     */
    @Test
    @DisplayName("测试审查 Prompt 生成")
    void testPromptGeneration() {
        // 测试不同风格的系统提示
        String[] styles = {"professional", "sarcastic", "gentle", "humorous"};

        for (String style : styles) {
            String systemPrompt = PromptTemplate.buildSystemPrompt(style);
            assertNotNull(systemPrompt);
            assertTrue(systemPrompt.contains("资深软件工程师"));
            System.out.println("✅ 风格 '" + style + "' Prompt 生成成功");
        }

        // 测试用户提示
        List<CodeChange> changes = new ArrayList<>();
        CodeChange change = new CodeChange();
        change.setFilePath("src/main/java/Service.java");
        change.setChangeType("modify");
        change.setAdditions(10);
        change.setDeletions(5);
        change.setDiff("+new code\n-old code");
        changes.add(change);

        String userPrompt = PromptTemplate.buildUserPrompt(changes, "fix: 修复bug");
        assertTrue(userPrompt.contains("Service.java"));
        assertTrue(userPrompt.contains("+10"));
        assertTrue(userPrompt.contains("-5"));
        assertTrue(userPrompt.contains("fix: 修复bug"));
    }

    /**
     * 测试评分提取
     */
    @Test
    @DisplayName("测试评分提取功能")
    void testScoreExtraction() {
        // 测试各种格式的评分提取
        String[] reviewResults = {
            "审查完成\n**总分：85分**",
            "审查结果\n**总分:92分**",
            "{\"score\": 88, \"review\": \"...\"}",
            "没有明确分数的审查结果"
        };
        int[] expectedScores = {85, 92, 88, 70};

        for (int i = 0; i < reviewResults.length; i++) {
            int score = PromptTemplate.extractScore(reviewResults[i]);
            assertEquals(expectedScores[i], score);
            System.out.println("✅ 评分提取: '" + reviewResults[i].substring(0, Math.min(20, reviewResults[i].length())) + "...' -> " + score);
        }
    }

    /**
     * 测试 Token 计数和截断
     */
    @Test
    @DisplayName("测试 Token 计数和截断")
    void testTokenCountingAndTruncation() {
        // 模拟大文件代码
        StringBuilder largeCode = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            largeCode.append("// This is a comment line ").append(i).append("\n");
            largeCode.append("public void method").append(i).append("() {\n");
            largeCode.append("    System.out.println(\"Hello World\");\n");
            largeCode.append("}\n");
        }

        String largeText = largeCode.toString();
        int estimatedTokens = TokenCounter.estimateTokens(largeText);

        System.out.println("✅ 大文件 Token 估算: " + estimatedTokens);

        // 测试截断
        int maxTokens = 1000;
        String truncated = TokenCounter.truncate(largeText, maxTokens);

        if (estimatedTokens > maxTokens) {
            assertTrue(truncated.contains("截断"));
            assertTrue(TokenCounter.estimateTokens(truncated) <= maxTokens * 1.2);
            System.out.println("✅ 大文件已截断到约 " + maxTokens + " tokens");
        }
    }

    /**
     * 测试中文代码注释的 Token 估算
     */
    @Test
    @DisplayName("测试中文代码注释 Token 估算")
    void testChineseCodeComments() {
        // 模拟包含中文注释的代码
        String codeWithChinese = """
            /**
             * 代码审查服务
             * 负责处理 Git 平台的代码审查请求
             */
            @Service
            public class CodeReviewService {
                /**
                 * 处理 GitLab 合并请求
                 * @param payload Webhook 载荷
                 */
                public void handleGitLabMergeRequest(String payload) {
                    // 解析请求数据
                    JSONObject json = JSONUtil.parseObj(payload);
                    // 获取代码变更
                    List<CodeChange> changes = client.fetchChanges(json);
                    // 调用 AI 进行审查
                    ReviewResult result = reviewCode(changes);
                }
            }
            """;

        TokenCounter.TokenStats stats = TokenCounter.getStats(codeWithChinese);

        System.out.println("✅ 中文代码注释统计:");
        System.out.println("   中文字符: " + stats.chineseChars());
        System.out.println("   其他字符: " + stats.otherChars());
        System.out.println("   估算 Token: " + stats.estimatedTokens());

        assertTrue(stats.chineseChars() > 0, "应该检测到中文字符");
        assertTrue(stats.estimatedTokens() > 0);
    }
}
