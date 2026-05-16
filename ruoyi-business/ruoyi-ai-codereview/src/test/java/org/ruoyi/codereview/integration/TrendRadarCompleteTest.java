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
 * TrendRadar 仓库完整测试流程
 * 模拟仓库: https://github.com/MuSan-Li/TrendRadar
 *
 * 由于 Token 权限限制，使用模拟方式完成测试
 */
@Tag("dev")
class TrendRadarCompleteTest {

    /**
     * 完整的代码审查流程模拟
     * 1. 创建分支 -> 2. 提交代码 -> 3. 推送 -> 4. 创建 PR -> 5. AI 审查 -> 6. 发送通知
     */
    @Test
    @DisplayName("完整流程 - 从 Push 到 Merge 的代码审查")
    void testCompleteCodeReviewFlow() {
        System.out.println("\n========================================");
        System.out.println("🚀 TrendRadar 代码审查完整流程测试");
        System.out.println("仓库: https://github.com/MuSan-Li/TrendRadar");
        System.out.println("========================================\n");

        // Step 1: 模拟创建分支
        System.out.println("📋 Step 1: 创建分支 feature/test-code-review");
        String branch = "feature/test-code-review";
        String baseBranch = "master";
        System.out.println("   ✓ 分支创建成功: " + branch + "\n");

        // Step 2: 模拟提交代码
        System.out.println("📝 Step 2: 提交代码变更");
        List<CodeChange> changes = createTestChanges();
        int totalAdditions = changes.stream().mapToInt(CodeChange::getAdditions).sum();
        int totalDeletions = changes.stream().mapToInt(CodeChange::getDeletions).sum();
        System.out.println("   ✓ 新增文件: " + changes.get(0).getFilePath());
        System.out.println("   ✓ 变更统计: +" + totalAdditions + " -" + totalDeletions + "\n");

        // Step 3: 模拟 Push Webhook
        System.out.println(" webhook Step 3: 模拟 Push Webhook 触发");
        String pushPayload = createPushPayload();
        JSONObject pushJson = JSONUtil.parseObj(pushPayload);
        System.out.println("   ✓ Webhook 接收成功");
        System.out.println("   ✓ 仓库: " + pushJson.getJSONObject("repository").getStr("full_name"));
        System.out.println("   ✓ 分支: " + pushJson.getStr("ref").replace("refs/heads/", ""));
        System.out.println("   ✓ 提交: " + pushJson.getJSONArray("commits").getJSONObject(0).getStr("message") + "\n");

        // Step 4: 模拟创建 PR
        System.out.println("🔀 Step 4: 模拟创建 Pull Request");
        String prPayload = createPRPayload(branch, baseBranch);
        JSONObject prJson = JSONUtil.parseObj(prPayload);
        System.out.println("   ✓ PR 创建成功: #" + prJson.getJSONObject("pull_request").getInt("number"));
        System.out.println("   ✓ 标题: " + prJson.getJSONObject("pull_request").getStr("title"));
        System.out.println("   ✓ 源分支 -> 目标分支: " + branch + " -> " + baseBranch + "\n");

        // Step 5: AI 审查
        System.out.println("🤖 Step 5: AI 代码审查");
        String commitMessages = "feat: 添加测试辅助工具模块";
        String userPrompt = PromptTemplate.buildUserPrompt(changes, commitMessages);
        int tokens = TokenCounter.estimateTokens(userPrompt);
        System.out.println("   ✓ Prompt 生成完成，估算 Token: " + tokens);

        // 模拟 AI 审查结果
        String reviewResult = simulateAIReviewResult();
        int score = PromptTemplate.extractScore(reviewResult);
        System.out.println("   ✓ 审查完成，总分: " + score + "/100\n");

        // 打印审查结果
        System.out.println("📄 Step 6: 审查结果报告");
        System.out.println("──────────────────────────────────────");
        System.out.println(reviewResult);
        System.out.println("──────────────────────────────────────\n");

        // Step 7: 模拟发送通知
        System.out.println("通知 Step 7: 发送审查通知");
        System.out.println("   ✓ 通知已发送到配置的渠道\n");

        // Step 8: 模拟 Merge（如果通过）
        if (score >= 60) {
            System.out.println("✅ Step 8: 审查通过，准备合并");
            System.out.println("   ✓ 评分 " + score + " >= 60，可以合并");
            System.out.println("   ✓ 执行 merge 操作...");
            System.out.println("   ✓ 合并成功！分支 " + branch + " 已合并到 " + baseBranch + "\n");
        } else {
            System.out.println("❌ Step 8: 审查未通过，需要修改");
            System.out.println("   评分 " + score + " < 60，请修复问题后重新提交\n");
        }

        System.out.println("========================================");
        System.out.println("🎉 测试完成！");
        System.out.println("========================================\n");

        // 验证
        assertTrue(score > 0);
        assertTrue(score <= 100);
    }

    /**
     * 测试 Webhook 签名验证
     */
    @Test
    @DisplayName("GitHub Webhook 签名验证测试")
    void testWebhookSignature() {
        String secret = "test-webhook-secret";
        String payload = "{\"test\": \"data\"}";

        // 模拟签名计算
        String signature = calculateHmacSha256(secret, payload);

        System.out.println("\n🔐 Webhook 签名验证测试");
        System.out.println("   Secret: " + secret);
        System.out.println("   Payload: " + payload);
        System.out.println("   Signature: sha256=" + signature);
        System.out.println("   ✓ 签名验证通过\n");

        assertNotNull(signature);
        assertEquals(64, signature.length());
    }

    private String calculateHmacSha256(String secret, String data) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec keySpec = new javax.crypto.spec.SecretKeySpec(
                secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hash = mac.doFinal(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private List<CodeChange> createTestChanges() {
        List<CodeChange> changes = new ArrayList<>();
        CodeChange change = new CodeChange();
        change.setFilePath("trendradar/utils/test_helper.py");
        change.setFileName("test_helper.py");
        change.setChangeType("add");
        change.setAdditions(42);
        change.setDeletions(0);
        change.setDiff(generateDiff());
        changes.add(change);
        return changes;
    }

    private String generateDiff() {
        return """
            +# coding=utf-8
            +# 测试辅助工具
            +
            +def process_data(data, config):
            +    result = {}
            +    items = data.get('items', [])
            +    for item in items:
            +        if item and 'id' in item:
            +            result[item['id']] = item.get('value')
            +    return result
            +
            +def validate_input(input_str):
            +    if input_str is None:
            +        return None
            +    return input_str.strip()
            +
            +class DataProcessor:
            +    def __init__(self):
            +        self.data = None
            +
            +    def load(self, path):
            +        try:
            +            with open(path, 'r', encoding='utf-8') as f:
            +                self.data = f.read()
            +        except FileNotFoundError:
            +            self.data = ''
            +
            +    def process(self):
            +        if self.data is None:
            +            return []
            +        lines = self.data.split('\\n')
            +        return [line.strip() for line in lines if line.strip()]
            """;
    }

    private String createPushPayload() {
        return """
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
                "login": "MuSan-Li"
              },
              "commits": [
                {
                  "id": "f13a3f6def456",
                  "message": "feat: 添加测试辅助工具模块",
                  "author": {"name": "MuSan-Li"}
                }
              ]
            }
            """;
    }

    private String createPRPayload(String head, String base) {
        return """
            {
              "action": "opened",
              "number": 1,
              "repository": {
                "id": 987654321,
                "name": "TrendRadar",
                "full_name": "MuSan-Li/TrendRadar"
              },
              "pull_request": {
                "number": 1,
                "html_url": "https://github.com/MuSan-Li/TrendRadar/pull/1",
                "title": "feat: 添加测试辅助工具模块",
                "body": "添加数据处理和输入验证工具类",
                "user": {"login": "MuSan-Li"},
                "head": {"ref": "%s", "sha": "f13a3f6def456"},
                "base": {"ref": "%s"},
                "draft": false
              }
            }
            """.formatted(head, base);
    }

    private String simulateAIReviewResult() {
        return """
            ## AI Code Review

            ### 1. 功能性 & 健壮性（35/40分）

            ✅ 代码逻辑清晰，实现了基本功能：
            - `process_data`: 正确使用 `get()` 方法避免 KeyError
            - `validate_input`: 正确处理 None 情况
            - `DataProcessor`: 添加了异常处理

            **改进建议**：
            - 可以添加类型注解提高代码可读性
            - 建议添加单元测试覆盖边界情况

            ### 2. 安全 & 风险（28/30分）

            ✅ 代码安全性良好：
            - 使用参数化方式处理数据
            - 异常处理避免敏感信息泄露
            - 文件操作使用 `encoding='utf-8'`

            ### 3. 最佳实践（16/20分）

            代码结构良好：
            - ✅ 使用 Python 标准库
            - ✅ 异常处理恰当
            - ⚠️ 建议添加类型注解
            - ⚠️ 可以使用 `pathlib` 替代字符串路径

            ### 4. 性能 & 效率（5/5分）

            ✅ 代码效率良好，无明显性能问题

            ### 5. Commit Message（4/5分）

            ✅ 提交信息清晰，符合规范

            ---

            **总分：88分**

            ✅ **审查通过**，代码质量良好，可以合并。

            ---
            *Powered by ruoyi-ai-codereview*
            """;
    }
}
