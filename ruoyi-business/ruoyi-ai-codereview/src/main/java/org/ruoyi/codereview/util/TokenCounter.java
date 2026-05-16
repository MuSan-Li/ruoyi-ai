package org.ruoyi.codereview.util;

/**
 * Token 计数与截断工具
 * <p>
 * 支持中英文混合文本的 Token 估算：
 * - 英文/数字/符号：约 4 字符 = 1 token
 * - 中文：约 1.5 字符 = 1 token
 * </p>
 */
public class TokenCounter {

    /**
     * 英文平均字符数/Token
     */
    private static final double CHARS_PER_TOKEN_ENGLISH = 4.0;

    /**
     * 中文平均字符数/Token
     */
    private static final double CHARS_PER_TOKEN_CHINESE = 1.5;

    /**
     * 判断是否为中文字符
     */
    private static boolean isChinese(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
            || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
            || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
            || block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
            || block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS;
    }

    /**
     * 估算 token 数量（支持中英文混合）
     */
    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;

        int chineseChars = 0;
        int otherChars = 0;

        for (char c : text.toCharArray()) {
            if (isChinese(c)) {
                chineseChars++;
            } else {
                otherChars++;
            }
        }

        // 分别计算中文和英文的 token 数
        double chineseTokens = chineseChars / CHARS_PER_TOKEN_CHINESE;
        double englishTokens = otherChars / CHARS_PER_TOKEN_ENGLISH;

        return (int) Math.ceil(chineseTokens + englishTokens);
    }

    /**
     * 按最大 token 数截断文本（智能截断）
     *
     * @param text      原文本
     * @param maxTokens 最大 token 数
     * @return 截断后的文本
     */
    public static String truncate(String text, int maxTokens) {
        if (text == null || text.isEmpty()) return text;

        // 先估算当前 token 数
        if (estimateTokens(text) <= maxTokens) {
            return text;
        }

        // 计算目标字符数（保守估计，使用英文比例）
        int targetChars = (int) (maxTokens * CHARS_PER_TOKEN_ENGLISH * 0.8);

        if (text.length() <= targetChars) {
            return text;
        }

        return text.substring(0, targetChars) + "\n\n... (内容已截断，超出最大 token 限制)";
    }

    /**
     * 获取文本统计信息（用于调试）
     */
    public static TokenStats getStats(String text) {
        if (text == null || text.isEmpty()) {
            return new TokenStats(0, 0, 0);
        }

        int chineseChars = 0;
        int otherChars = 0;

        for (char c : text.toCharArray()) {
            if (isChinese(c)) {
                chineseChars++;
            } else {
                otherChars++;
            }
        }

        return new TokenStats(chineseChars, otherChars, estimateTokens(text));
    }

    /**
     * Token 统计信息
     */
    public record TokenStats(int chineseChars, int otherChars, int estimatedTokens) {
        @Override
        public String toString() {
            return String.format("TokenStats{chinese=%d, other=%d, tokens=%d}",
                chineseChars, otherChars, estimatedTokens);
        }
    }
}
