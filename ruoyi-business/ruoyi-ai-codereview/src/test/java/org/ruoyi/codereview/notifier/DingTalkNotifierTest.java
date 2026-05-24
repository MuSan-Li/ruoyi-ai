package org.ruoyi.codereview.notifier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DingTalkNotifier 单元测试
 * <p>
 * 注：由于 DingTalkNotifier 使用静态 HttpUtil，真正的集成测试需要使用 mock-server
 * 这里主要测试构造函数和基本行为
 */
@Tag("dev")
class DingTalkNotifierTest {

    @Test
    @DisplayName("构造函数 - 无签名模式")
    void constructor_noSecret() {
        DingTalkNotifier notifier = new DingTalkNotifier(
                "https://oapi.dingtalk.com/robot/send?access_token=test",
                false,
                null
        );

        assertNotNull(notifier);
    }

    @Test
    @DisplayName("构造函数 - 签名模式")
    void constructor_withSecret() {
        DingTalkNotifier notifier = new DingTalkNotifier(
                "https://oapi.dingtalk.com/robot/send?access_token=test",
                true,
                "SEC123456"
        );

        assertNotNull(notifier);
    }

    @Test
    @DisplayName("send - 无效 URL 返回 false")
    void send_invalidUrl_returnsFalse() {
        DingTalkNotifier notifier = new DingTalkNotifier(
                "invalid-url",
                false,
                null
        );

        boolean result = notifier.send("测试标题", "测试内容");

        // 无效 URL 应该失败
        assertFalse(result);
    }

    @Test
    @DisplayName("send - 空内容不抛异常")
    void send_emptyContent_noException() {
        DingTalkNotifier notifier = new DingTalkNotifier(
                "https://oapi.dingtalk.com/robot/send?access_token=test",
                false,
                null
        );

        // 不应该抛出异常（可能会返回 false）
        assertDoesNotThrow(() -> notifier.send("", ""));
        assertDoesNotThrow(() -> notifier.send(null, null));
    }

    @Test
    @DisplayName("send - 带签名的请求")
    void send_withSecret_enabled() {
        DingTalkNotifier notifier = new DingTalkNotifier(
                "https://oapi.dingtalk.com/robot/send?access_token=test",
                true,
                "test-secret-123"
        );

        // 由于网络请求会失败，预期返回 false
        // 但主要验证签名逻辑不会抛异常
        assertDoesNotThrow(() -> {
            notifier.send("测试标题", "测试内容");
        });
    }

    @Test
    @DisplayName("send - 签名启用但 secret 为空")
    void send_secretEnabledButEmptySecret() {
        DingTalkNotifier notifier = new DingTalkNotifier(
                "https://oapi.dingtalk.com/robot/send?access_token=test",
                true,
                ""
        );

        // secret 为空时不应该崩溃
        assertDoesNotThrow(() -> {
            notifier.send("测试标题", "测试内容");
        });
    }

    @Test
    @DisplayName("send - Markdown 格式内容")
    void send_markdownContent() {
        DingTalkNotifier notifier = new DingTalkNotifier(
                "https://oapi.dingtalk.com/robot/send?access_token=test",
                false,
                null
        );

        String markdownContent = """
            ## 代码审查报告

            - **项目**: test-project
            - **分支**: feature/test
            - **评分**: 85/100

            ### 问题列表
            1. 空指针风险
            2. SQL 注入隐患
            """;

        // 不应该抛出异常
        assertDoesNotThrow(() -> {
            notifier.send("代码审查报告", markdownContent);
        });
    }
}
