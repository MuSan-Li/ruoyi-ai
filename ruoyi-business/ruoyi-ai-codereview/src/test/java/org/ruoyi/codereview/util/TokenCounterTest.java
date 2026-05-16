package org.ruoyi.codereview.util;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TokenCounter 单元测试
 */
@Tag("dev")
class TokenCounterTest {

    @Test
    void estimateTokens_nullInput_returnsZero() {
        assertEquals(0, TokenCounter.estimateTokens(null));
    }

    @Test
    void estimateTokens_emptyString_returnsZero() {
        assertEquals(0, TokenCounter.estimateTokens(""));
    }

    @Test
    void estimateTokens_englishText_returnsCorrectEstimate() {
        // 英文：4 字符 ≈ 1 token
        String text = "Hello World!"; // 12 chars, 全英文
        int tokens = TokenCounter.estimateTokens(text);
        assertTrue(tokens >= 2 && tokens <= 4, "英文文本 token 估算应在合理范围内");
    }

    @Test
    void estimateTokens_chineseText_returnsCorrectEstimate() {
        // 中文：1.5 字符 ≈ 1 token
        String text = "你好世界测试代码"; // 8 个中文字符
        int tokens = TokenCounter.estimateTokens(text);
        assertTrue(tokens >= 4 && tokens <= 8, "中文文本 token 估算应在合理范围内");
    }

    @Test
    void estimateTokens_mixedText_returnsCorrectEstimate() {
        // 中英混合
        String text = "Hello 世界 Test 代码"; // 混合文本
        int tokens = TokenCounter.estimateTokens(text);
        assertTrue(tokens > 0, "混合文本 token 估算应大于 0");
    }

    @Test
    void estimateTokens_longText_returnsCorrectEstimate() {
        String text = "a".repeat(100); // 100 chars 英文
        int tokens = TokenCounter.estimateTokens(text);
        assertTrue(tokens >= 20 && tokens <= 30, "长文本 token 估算应在合理范围内");
    }

    @Test
    void truncate_nullInput_returnsNull() {
        assertNull(TokenCounter.truncate(null, 100));
    }

    @Test
    void truncate_emptyString_returnsEmpty() {
        assertEquals("", TokenCounter.truncate("", 100));
    }

    @Test
    void truncate_shortText_returnsOriginal() {
        String text = "Hello World";
        assertEquals(text, TokenCounter.truncate(text, 100));
    }

    @Test
    void truncate_longText_truncatesCorrectly() {
        String text = "a".repeat(1000); // 1000 chars
        String result = TokenCounter.truncate(text, 10); // max 10 tokens
        assertTrue(result.contains("截断"));
        assertTrue(result.length() < text.length());
    }

    @Test
    void truncate_exactLimit_returnsOriginal() {
        String text = "Hello World"; // 短文本
        String result = TokenCounter.truncate(text, 100);
        assertEquals(text, result);
    }

    @Test
    void getStats_returnsCorrectStats() {
        String text = "Hello世界"; // 5 英文 + 2 中文，无空格
        TokenCounter.TokenStats stats = TokenCounter.getStats(text);
        assertEquals(2, stats.chineseChars());
        assertEquals(5, stats.otherChars());
        assertTrue(stats.estimatedTokens() > 0);
    }
}
