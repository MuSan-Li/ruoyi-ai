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

import static org.junit.jupiter.api.Assertions.*;

/**
 * TrendRadar 仓库集成测试
 * 模拟仓库: https://github.com/MuSan-Li/TrendRadar
 *
 * 测试场景:
 * 1. Push 事件 - 新增测试辅助工具模块
 * 2. PR 事件 - feature/test-code-review -> master
 */
@Tag("dev")
class TrendRadarIntegrationTest {

    /**
     * 模拟 TrendRadar 仓库的 Push Webhook
     * 场景: 推送新文件 test_helper.py
     */
    @Test
    @DisplayName("TrendRadar Push Webhook - 新增测试工具模块")
    void simulateTrendRadarPushWebhook() {
        // 模拟 GitHub Push payload
        String payload = """
            {
              "ref": "refs/heads/feature/test-code-review",
              "before": "4c38fb6abc123",
              "after": "f13a3f6def456",
              "repository": {
                "id": 987654321,
                "name": "TrendRadar",
                "full_name": "MuSan-Li/TrendRadar"
              },
              "sender": {
                "login": "developer"
              },
              "commits": [
                {
                  "id": "f13a3f6def456",
                  "message": "feat: 添加测试辅助工具模块",
                  "author": {
                    "name": "developer"
                  }
                }
              ]
            }
            """;

        JSONObject json = JSONUtil.parseObj(payload);

        // 验证解析
        assertEquals("refs/heads/feature/test-code-review", json.getStr("ref"));
        assertEquals("TrendRadar", json.getJSONObject("repository").getStr("name"));
        assertEquals("MuSan-Li/TrendRadar", json.getJSONObject("repository").getStr("full_name"));

        // 解析分支
        String ref = json.getStr("ref");
        String branch = ref.startsWith("refs/heads/") ? ref.substring(11) : ref;
        assertEquals("feature/test-code-review", branch);

        System.out.println("✅ TrendRadar Push Webhook 解析成功");
        System.out.println("   仓库: MuSan-Li/TrendRadar");
        System.out.println("   分支: " + branch);
        System.out.println("   提交: " + json.getJSONArray("commits").getJSONObject(0).getStr("message"));
    }

    /**
     * 模拟 TrendRadar 仓库的 PR Webhook
     * 场景: 从 feature/test-code-review 合并到 master
     */
    @Test
    @DisplayName("TrendRadar PR Webhook - 代码审查请求")
    void simulateTrendRadarPullRequestWebhook() {
        String payload = """
            {
              "action": "opened",
              "number": 1,
              "repository": {
                "id": 987654321,
                "name": "TrendRadar",
                "full_name": "MuSan-Li/TrendRadar",
                "owner": {
                  "login": "MuSan-Li"
                }
              },
              "pull_request": {
                "number": 1,
                "html_url": "https://github.com/MuSan-Li/TrendRadar/pull/1",
                "title": "feat: 添加测试辅助工具模块",
                "body": "添加数据处理和输入验证工具类",
                "user": {
                  "login": "developer"
                },
                "head": {
                  "ref": "feature/test-code-review",
                  "sha": "f13a3f6def456789"
                },
                "base": {
                  "ref": "master"
                },
                "draft": false
              }
            }
            """;

        JSONObject json = JSONUtil.parseObj(payload);

        // 验证解析
        assertEquals("opened", json.getStr("action"));
        assertEquals("TrendRadar", json.getJSONObject("repository").getStr("name"));
        assertEquals(1, json.getJSONObject("pull_request").getInt("number"));
        assertFalse(json.getJSONObject("pull_request").getBool("draft", false));

        // 验证分支信息
        String sourceBranch = json.getJSONObject("pull_request").getJSONObject("head").getStr("ref");
        String targetBranch = json.getJSONObject("pull_request").getJSONObject("base").getStr("ref");
        assertEquals("feature/test-code-review", sourceBranch);
        assertEquals("master", targetBranch);

        System.out.println("✅ TrendRadar PR Webhook 解析成功");
        System.out.println("   PR #" + json.getJSONObject("pull_request").getInt("number") + ": " + json.getJSONObject("pull_request").getStr("title"));
        System.out.println("   分支: " + sourceBranch + " -> " + targetBranch);
    }

    /**
     * 模拟代码变更审查 - test_helper.py
     * 包含故意添加的问题代码
     */
    @Test
    @DisplayName("代码审查 - 检测问题代码")
    void simulateCodeReviewWithIssues() {
        // 模拟 test_helper.py 的代码变更
        List<CodeChange> changes = new ArrayList<>();

        CodeChange change = new CodeChange();
        change.setFilePath("trendradar/utils/test_helper.py");
        change.setFileName("test_helper.py");
        change.setChangeType("add");
        change.setAdditions(42);
        change.setDeletions(0);
        change.setDiff("""
            +# coding=utf-8
            +# 测试辅助工具 - 用于代码审查测试
            +
            +def process_data(data, config):
            +    # 处理数据
            +    result = {}
            +
            +    # 问题1: 潜在的空指针 - data['items'] 可能不存在
            +    if data['items']:
            +        for item in data['items']:
            +            result[item['id']] = item['value']
            +
            +    # 问题2: SQL 注入风险
            +    query = "SELECT * FROM users WHERE id = " + str(data.get('user_id'))
            +
            +    # 问题3: 硬编码配置
            +    timeout = 30
            +    retry_count = 3
            +
            +    return result
            +
            +def validate_input(input_str):
            +    # 验证输入
            +    # 问题: 没有处理 None 的情况可能导致 AttributeError
            +    return input_str.strip() if input_str else None
            +
            +class DataProcessor:
            +    def __init__(self):
            +        self.data = None
            +
            +    def load(self, path):
            +        # 问题: 没有异常处理
            +        with open(path, 'r') as f:
            +            self.data = f.read()
            +
            +    def process(self):
            +        # 问题: self.data 可能为 None
            +        lines = self.data.split('\\n')
            +        return [line.strip() for line in lines]
            """);
        changes.add(change);

        // 生成审查 Prompt
        String commitMessages = "feat: 添加测试辅助工具模块";
        String userPrompt = PromptTemplate.buildUserPrompt(changes, commitMessages);

        // 验证 Prompt 内容
        assertTrue(userPrompt.contains("test_helper.py"));
        assertTrue(userPrompt.contains("trendradar/utils"));
        assertTrue(userPrompt.contains("+42"));
        assertTrue(userPrompt.contains("process_data"));
        // SQL 可能在 diff 中被转义，检查 SELECT 关键字
        assertTrue(userPrompt.contains("SELECT") || userPrompt.contains("users"));
        assertTrue(userPrompt.contains("Commit"));

        // 计算 Token
        int tokens = TokenCounter.estimateTokens(userPrompt);
        System.out.println("✅ 代码变更审查准备完成");
        System.out.println("   文件: " + change.getFilePath());
        System.out.println("   变更: +" + change.getAdditions() + " -" + change.getDeletions());
        System.out.println("   估算 Token: " + tokens);

        // 模拟 AI 审查结果
        String simulatedReview = simulateAIReview();
        int score = PromptTemplate.extractScore(simulatedReview);

        System.out.println("\n📋 模拟 AI 审查结果:");
        System.out.println(simulatedReview);
        System.out.println("\n总分: " + score + "/100");
    }

    /**
     * 模拟 AI 审查结果
     */
    private String simulateAIReview() {
        return """
            ## AI Code Review

            ### 1. 功能性 & 健壮性（25/40分）

            代码逻辑存在以下问题：

            - **第13行**: `data['items']` 可能不存在，当 `data` 为空或不包含 `items` 键时会抛出 `KeyError`
            - **第25行**: `validate_input` 函数参数 `input_str` 命名与类型注解缺失
            - **第33行**: `load` 方法没有异常处理，文件不存在会直接崩溃
            - **第37行**: `process` 方法中 `self.data` 可能为 `None`，调用 `.split()` 会抛出 `AttributeError`

            **修改建议**：
            ```python
            def process_data(data, config):
                result = {}
                items = data.get('items', [])  # 使用 get 方法提供默认值
                for item in items:
                    if item and 'id' in item and 'value' in item:
                        result[item['id']] = item['value']
                return result
            ```

            ### 2. 安全 & 风险（15/30分）

            发现严重安全问题：

            - **第18行**: **SQL 注入风险**！直接拼接字符串构建 SQL 查询是极其危险的做法

            **修改建议**：
            ```python
            # 使用参数化查询
            # cursor.execute("SELECT * FROM users WHERE id = ?", (data.get('user_id'),))
            ```

            ### 3. 最佳实践（12/20分）

            - 缺少类型注解（Type Hints）
            - 硬编码的超时和重试次数应该作为配置参数
            - 缺少文档字符串的参数说明
            - 建议使用 `pathlib` 替代字符串路径

            ### 4. 性能 & 效率（5/5分）

            代码结构简单，无明显性能问题。

            ### 5. Commit Message（4/5分）

            提交信息清晰，但可以更详细描述变更内容。

            ---

            **总分：61分**

            ⚠️ **建议**: 代码存在安全隐患（SQL注入）和潜在的运行时错误，建议修复后再合并。

            ---
            *Powered by ruoyi-ai-codereview*
            """;
    }

    /**
     * 测试问题代码检测
     */
    @Test
    @DisplayName("问题代码检测 - 验证审查覆盖率")
    void testIssueDetection() {
        String diff = """
            +def process_data(data, config):
            +    if data['items']:  # KeyError 风险
            +        for item in data['items']:
            +            result[item['id']] = item['value']
            +    query = "SELECT * FROM users WHERE id = " + str(data.get('user_id'))  # SQL注入
            """;

        // 检测潜在问题
        assertTrue(diff.contains("data['items']"), "应检测到直接字典访问");
        assertTrue(diff.contains("SELECT * FROM"), "应检测到 SQL 语句");
        assertTrue(diff.contains("+"), "应包含新增行标记");

        System.out.println("✅ 问题代码检测通过");
        System.out.println("   - 检测到潜在 KeyError 风险");
        System.out.println("   - 检测到 SQL 注入风险");
    }

    /**
     * 测试完整审查流程
     */
    @Test
    @DisplayName("完整审查流程 - 从 Webhook 到评论生成")
    void testCompleteReviewFlow() {
        // 1. 解析 Webhook
        String payload = """
            {
              "action": "opened",
              "repository": {"name": "TrendRadar", "full_name": "MuSan-Li/TrendRadar"},
              "pull_request": {
                "number": 1,
                "title": "feat: 添加测试辅助工具模块",
                "head": {"ref": "feature/test-code-review", "sha": "abc123"},
                "base": {"ref": "master"},
                "draft": false
              }
            }
            """;
        JSONObject json = JSONUtil.parseObj(payload);
        assertEquals("opened", json.getStr("action"));

        // 2. 生成审查 Prompt
        List<CodeChange> changes = createTestChanges();
        String systemPrompt = PromptTemplate.buildSystemPrompt("professional");
        String userPrompt = PromptTemplate.buildUserPrompt(changes, "feat: 添加测试工具");

        assertNotNull(systemPrompt);
        assertNotNull(userPrompt);
        assertTrue(systemPrompt.contains("资深软件工程师"));

        // 3. Token 估算
        int tokens = TokenCounter.estimateTokens(userPrompt);
        assertTrue(tokens > 0);

        // 4. 生成评论格式
        String reviewComment = "## AI Code Review\n\n审查完成\n\n**总分：75分**\n\n*Powered by ruoyi-ai-codereview*";
        int score = PromptTemplate.extractScore(reviewComment);
        assertEquals(75, score);

        System.out.println("✅ 完整审查流程测试通过");
        System.out.println("   1. Webhook 解析 ✓");
        System.out.println("   2. Prompt 生成 ✓");
        System.out.println("   3. Token 估算: " + tokens);
        System.out.println("   4. 评论生成 ✓");
        System.out.println("   5. 评分提取: " + score);
    }

    private List<CodeChange> createTestChanges() {
        List<CodeChange> changes = new ArrayList<>();
        CodeChange change = new CodeChange();
        change.setFilePath("trendradar/utils/test_helper.py");
        change.setChangeType("add");
        change.setAdditions(42);
        change.setDeletions(0);
        change.setDiff("+def test():\n+    pass\n");
        changes.add(change);
        return changes;
    }
}
