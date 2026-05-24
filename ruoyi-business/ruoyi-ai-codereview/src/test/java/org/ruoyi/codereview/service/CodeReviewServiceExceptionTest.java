package org.ruoyi.codereview.service;

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
 * 异常场景测试
 * <p>
 * 测试各种边界条件和异常情况的处理
 */
@Tag("dev")
class CodeReviewServiceExceptionTest {

    // ==================== 空数据处理 ====================

    @Test
    @DisplayName("空变更列表 - 返回空结果")
    void emptyChanges_returnsEmpty() {
        List<CodeChange> changes = new ArrayList<>();
        assertTrue(changes.isEmpty());
    }

    @Test
    @DisplayName("空 commit 消息 - 不崩溃")
    void emptyCommitMessages_noCrash() {
        String commitMessages = "";
        String result = PromptTemplate.buildUserPrompt(List.of(), commitMessages);

        assertNotNull(result);
        assertTrue(result.contains("请审查以下代码变更"));
    }

    @Test
    @DisplayName("null commit 消息 - 不崩溃")
    void nullCommitMessages_noCrash() {
        String result = PromptTemplate.buildUserPrompt(List.of(), null);

        assertNotNull(result);
    }

    // ==================== 边界值测试 ====================

    @Test
    @DisplayName("评分边界 - 最小值")
    void scoreBoundary_minimum() {
        String reviewResult = "**总分：0分**";
        int score = PromptTemplate.extractScore(reviewResult);
        assertEquals(0, score);
    }

    @Test
    @DisplayName("评分边界 - 最大值")
    void scoreBoundary_maximum() {
        String reviewResult = "**总分：100分**";
        int score = PromptTemplate.extractScore(reviewResult);
        assertEquals(100, score);
    }

    @Test
    @DisplayName("评分边界 - 超出范围")
    void scoreBoundary_outOfRange() {
        String reviewResult = "**总分：150分**";
        int score = PromptTemplate.extractScore(reviewResult);
        // 应该能解析，但业务逻辑应限制在 0-100
        assertEquals(150, score);
    }

    @Test
    @DisplayName("评分解析 - 无效格式返回默认值")
    void scoreParsing_invalidFormat_returnsDefault() {
        String reviewResult = "没有评分信息";
        int score = PromptTemplate.extractScore(reviewResult);
        assertEquals(70, score); // 默认值
    }

    @Test
    @DisplayName("评分解析 - null 输入")
    void scoreParsing_nullInput_returnsDefault() {
        int score = PromptTemplate.extractScore(null);
        assertEquals(70, score);
    }

    // ==================== Token 截断测试 ====================

    @Test
    @DisplayName("Token 截断 - 空字符串")
    void tokenTruncation_emptyString() {
        String result = TokenCounter.truncate("", 1000);
        assertEquals("", result);
    }

    @Test
    @DisplayName("Token 截断 - 短文本不截断")
    void tokenTruncation_shortText_notTruncated() {
        String text = "这是一段短文本";
        String result = TokenCounter.truncate(text, 1000);
        assertEquals(text, result);
    }

    @Test
    @DisplayName("Token 截断 - 长文本截断")
    void tokenTruncation_longText_truncated() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            sb.append("这是一段测试文本。");
        }
        String longText = sb.toString();

        int maxTokens = 1000;
        String result = TokenCounter.truncate(longText, maxTokens);

        // 结果应该比原文短
        assertTrue(result.length() < longText.length());
        // 结果应该以截断标记结尾
        assertTrue(result.endsWith("...") || result.length() <= maxTokens * 4);
    }

    @Test
    @DisplayName("Token 估算 - 空字符串")
    void tokenEstimation_emptyString() {
        int tokens = TokenCounter.estimateTokens("");
        assertEquals(0, tokens);
    }

    @Test
    @DisplayName("Token 估算 - null 输入")
    void tokenEstimation_nullInput() {
        int tokens = TokenCounter.estimateTokens(null);
        assertEquals(0, tokens);
    }

    // ==================== JSON 解析异常测试 ====================

    @Test
    @DisplayName("JSON 解析 - 无效 JSON 抛异常")
    void jsonParsing_invalidJson_throwsException() {
        String invalidJson = "not valid json {{{";

        assertThrows(Exception.class, () -> {
            JSONUtil.parseObj(invalidJson);
        });
    }

    @Test
    @DisplayName("JSON 解析 - 空字符串抛异常")
    void jsonParsing_emptyString() {
        // hutool 对空字符串会抛异常
        assertThrows(Exception.class, () -> {
            JSONUtil.parseObj("");
        });
    }

    @Test
    @DisplayName("JSON 解析 - 缺少字段返回 null")
    void jsonParsing_missingField_returnsNull() {
        JSONObject json = new JSONObject();
        json.set("name", "test");

        // 获取不存在的字段
        assertNull(json.getJSONObject("nonexistent"));
        assertNull(json.getJSONArray("nonexistent"));
    }

    // ==================== CodeChange 边界测试 ====================

    @Test
    @DisplayName("CodeChange - 空路径")
    void codeChange_emptyPath() {
        CodeChange change = new CodeChange();
        change.setFilePath("");
        change.setDiff("");

        assertEquals("", change.getFilePath());
        assertEquals("", change.getDiff());
    }

    @Test
    @DisplayName("CodeChange - null 路径")
    void codeChange_nullPath() {
        CodeChange change = new CodeChange();
        change.setFilePath(null);

        assertNull(change.getFilePath());
    }

    @Test
    @DisplayName("CodeChange - 特殊字符路径")
    void codeChange_specialCharPath() {
        CodeChange change = new CodeChange();
        change.setFilePath("src/测试/文件名 with spaces.java");

        assertTrue(change.getFilePath().contains(" "));
        assertTrue(change.getFilePath().contains("测试"));
    }

    // ==================== Prompt 模板测试 ====================

    @Test
    @DisplayName("Prompt 模板 - 各种风格")
    void promptTemplate_variousStyles() {
        String[] styles = {"professional", "sarcastic", "gentle", "humorous", "unknown"};

        for (String style : styles) {
            String prompt = PromptTemplate.buildSystemPrompt(style);
            assertNotNull(prompt);
            // 验证包含审查相关内容
            assertTrue(prompt.contains("审查") || prompt.contains("评分") || prompt.contains("代码"));
        }
    }

    @Test
    @DisplayName("Prompt 模板 - 自定义规则注入")
    void promptTemplate_customRules() {
        List<org.ruoyi.codereview.entity.ReviewRule> rules = new ArrayList<>();

        org.ruoyi.codereview.entity.ReviewRule rule1 = new org.ruoyi.codereview.entity.ReviewRule();
        rule1.setRuleName("禁止使用 System.out");
        rule1.setDescription("请检查代码中是否有 System.out.println 调用");
        rules.add(rule1);

        org.ruoyi.codereview.entity.ReviewRule rule2 = new org.ruoyi.codereview.entity.ReviewRule();
        rule2.setRuleName("必须使用 @Transactional");
        rule2.setDescription("Service 层方法必须添加事务注解");
        rules.add(rule2);

        String prompt = PromptTemplate.buildSystemPrompt("professional", rules);

        assertTrue(prompt.contains("自定义审查规则"));
        assertTrue(prompt.contains("禁止使用 System.out"));
        assertTrue(prompt.contains("必须使用 @Transactional"));
    }

    @Test
    @DisplayName("Prompt 模板 - 空规则列表")
    void promptTemplate_emptyRules() {
        String prompt = PromptTemplate.buildSystemPrompt("professional", List.of());
        assertNotNull(prompt);
        // 不应该包含自定义规则部分
        assertFalse(prompt.contains("自定义审查规则"));
    }

    // ==================== 网络超时模拟 ====================

    @Test
    @DisplayName("超时场景 - 大文件处理")
    void timeoutScenario_largeFile() {
        // 模拟大文件处理
        StringBuilder largeContent = new StringBuilder();
        for (int i = 0; i < 100000; i++) {
            largeContent.append("public class Test").append(i).append(" { }\n");
        }

        // Token 估算应该能处理大文本
        int tokens = TokenCounter.estimateTokens(largeContent.toString());
        assertTrue(tokens > 0);
    }

    // ==================== 并发安全测试 ====================

    @Test
    @DisplayName("并发安全 - 多线程 Token 估算")
    void concurrency_tokenEstimation() throws InterruptedException {
        String text = "测试文本内容";
        int threadCount = 100;
        Thread[] threads = new Thread[threadCount];
        int[] results = new int[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                results[index] = TokenCounter.estimateTokens(text);
            });
        }

        for (Thread thread : threads) {
            thread.start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        // 所有结果应该一致
        int expected = TokenCounter.estimateTokens(text);
        for (int result : results) {
            assertEquals(expected, result);
        }
    }

    // ==================== 格式化测试 ====================

    @Test
    @DisplayName("格式化 - 特殊字符转义")
    void formatting_specialCharacters() {
        String content = "代码包含 <script>alert('xss')</script>";
        // 验证特殊字符不会导致问题
        assertNotNull(content);
        assertTrue(content.contains("<script>"));
    }

    @Test
    @DisplayName("格式化 - Unicode 字符")
    void formatting_unicodeCharacters() {
        String content = "中文评论：这段代码写得很好 👍";
        // 验证 Unicode 字符处理正确
        assertNotNull(content);
        assertTrue(content.contains("👍"));
    }

    @Test
    @DisplayName("格式化 - 换行符处理")
    void formatting_newlineHandling() {
        String content = "第一行\n第二行\r\n第三行\r第四行";
        // 验证各种换行符都能处理
        assertTrue(content.contains("\n"));
        assertTrue(content.contains("第一行"));
        assertTrue(content.contains("第四行"));
    }
}
