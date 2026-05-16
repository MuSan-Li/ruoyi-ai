package org.ruoyi.codereview.util;

import org.ruoyi.codereview.entity.CodeChange;

import java.util.List;

/**
 * Prompt 模板工具
 */
public class PromptTemplate {

    public static String buildSystemPrompt(String style) {
        String base = """
            你是一位资深软件工程师，负责审查代码变更。请从以下维度进行评分（总分100分）：

            1. 功能性 & 健壮性（40分）：逻辑是否正确、边界条件处理、异常处理
            2. 安全 & 风险（30分）：是否存在安全漏洞（SQL注入、XSS、越权等）、敏感信息泄露
            3. 最佳实践（20分）：代码风格、设计模式、可维护性、命名规范
            4. 性能 & 效率（5分）：是否存在性能问题、资源泄漏
            5. Commit Message 清晰度（5分）：提交信息是否清晰描述了变更内容

            输出要求：
            - 使用 Markdown 格式
            - 按评分维度分别说明问题和得分
            - 最后给出总分，格式为：**总分：XX分**
            - 如有问题，给出具体修改建议
            """;

        return switch (style) {
            case "sarcastic" -> base + "\n你的风格是幽默讽刺的，用犀利的语言指出问题，但技术分析一针见血。";
            case "gentle" -> base + "\n你的风格是温和友善的，建议为主鼓励为辅，用'建议'、'可以考虑'等措辞。";
            case "humorous" -> base + "\n你的风格是轻松幽默的，用生动比喻和emoji来解释问题。";
            default -> base + "\n你的风格是专业严谨的，直接指出问题并提供解决方案。";
        };
    }

    public static String buildUserPrompt(List<CodeChange> changes, String commitMessages) {
        StringBuilder sb = new StringBuilder();
        sb.append("请审查以下代码变更：\n\n");

        for (CodeChange change : changes) {
            sb.append("### 文件: ").append(change.getFilePath()).append("\n");
            sb.append("- 变更类型: ").append(change.getChangeType()).append("\n");
            sb.append("- 新增: +").append(change.getAdditions()).append(" 行, ");
            sb.append("删除: -").append(change.getDeletions()).append(" 行\n\n");

            if (change.getDiff() != null && !change.getDiff().isEmpty()) {
                sb.append("```diff\n").append(change.getDiff()).append("\n```\n\n");
            }
            sb.append("---\n\n");
        }

        if (commitMessages != null && !commitMessages.isEmpty()) {
            sb.append("### Commit 信息\n").append(commitMessages).append("\n");
        }

        return sb.toString();
    }

    /**
     * 从审查结果中提取总分
     */
    public static int extractScore(String reviewResult) {
        if (reviewResult == null) return 70;

        // 匹配 **总分：XX分** 格式
        java.util.regex.Pattern p1 = java.util.regex.Pattern.compile("\\*?\\*?总分[：:]\\s*(\\d+)\\s*分?\\*?\\*?");
        java.util.regex.Matcher m1 = p1.matcher(reviewResult);
        if (m1.find()) {
            return Integer.parseInt(m1.group(1));
        }

        // 匹配 "score": XX 格式（JSON）
        java.util.regex.Pattern p2 = java.util.regex.Pattern.compile("\"score\"\\s*:\\s*(\\d+)");
        java.util.regex.Matcher m2 = p2.matcher(reviewResult);
        if (m2.find()) {
            return Integer.parseInt(m2.group(1));
        }

        return 70;
    }
}
