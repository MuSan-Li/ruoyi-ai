package org.ruoyi.codereview.service;

import lombok.extern.slf4j.Slf4j;
import org.ruoyi.codereview.entity.CodeChange;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 增量审查服务
 * 只审查变更的代码行，而非整个文件
 */
@Slf4j
@Service
public class IncrementalReviewService {

    /** diff 行模式 */
    private static final Pattern DIFF_HUNK_PATTERN = Pattern.compile("@@ -(\\d+),(\\d+) \\+(\\d+),(\\d+) @@.*");
    private static final Pattern DIFF_ADD_PATTERN = Pattern.compile("^\\+(?!\\+)(.*)$", Pattern.MULTILINE);
    private static final Pattern DIFF_DEL_PATTERN = Pattern.compile("^-(?!-)(.*)$", Pattern.MULTILINE);

    /**
     * 提取变更行
     * 只保留新增和修改的代码行，减少审查内容
     *
     * @param change 代码变更
     * @return 增量变更内容
     */
    public IncrementalChange extractChangedLines(CodeChange change) {
        String diff = change.getDiff();
        if (diff == null || diff.isEmpty()) {
            return new IncrementalChange(change.getFilePath(), "", "", List.of(), List.of());
        }

        // 提取新增行
        List<CodeLine> addedLines = new ArrayList<>();
        Matcher addMatcher = DIFF_ADD_PATTERN.matcher(diff);
        while (addMatcher.find()) {
            String line = addMatcher.group(1);
            if (line == null) continue;
            line = line.trim();
            if (!line.isEmpty() && !isCommentOnly(line)) {
                addedLines.add(new CodeLine(line, "ADD"));
            }
        }

        // 提取删除行
        List<CodeLine> deletedLines = new ArrayList<>();
        Matcher delMatcher = DIFF_DEL_PATTERN.matcher(diff);
        while (delMatcher.find()) {
            String line = delMatcher.group(1);
            if (line == null) continue;
            line = line.trim();
            if (!line.isEmpty() && !isCommentOnly(line)) {
                deletedLines.add(new CodeLine(line, "DEL"));
            }
        }

        // 构建增量 diff
        StringBuilder incrementalDiff = new StringBuilder();
        incrementalDiff.append("### 新增代码 (").append(addedLines.size()).append(" 行)\n");
        for (CodeLine line : addedLines) {
            incrementalDiff.append("+ ").append(line.content()).append("\n");
        }

        if (!deletedLines.isEmpty()) {
            incrementalDiff.append("\n### 删除代码 (").append(deletedLines.size()).append(" 行)\n");
            for (CodeLine line : deletedLines) {
                incrementalDiff.append("- ").append(line.content()).append("\n");
            }
        }

        return new IncrementalChange(
            change.getFilePath(),
            incrementalDiff.toString(),
            diff,
            addedLines,
            deletedLines
        );
    }

    /**
     * 批量提取变更行
     */
    public List<IncrementalChange> extractChangedLines(List<CodeChange> changes) {
        return changes.stream()
            .map(this::extractChangedLines)
            .toList();
    }

    /**
     * 构建增量审查提示
     * 只包含变更的代码行，大幅减少 Token 消耗
     */
    public String buildIncrementalPrompt(List<IncrementalChange> changes, String commitMessages) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 代码变更审查\n\n");
        sb.append("请审查以下代码变更，重点关注：\n");
        sb.append("1. 新增代码的正确性和健壮性\n");
        sb.append("2. 删除代码是否会影响现有功能\n");
        sb.append("3. 变更是否引入安全风险\n\n");

        int totalAdditions = 0;
        int totalDeletions = 0;

        for (IncrementalChange change : changes) {
            totalAdditions += change.addedLines().size();
            totalDeletions += change.deletedLines().size();

            sb.append("---\n\n");
            sb.append("## 文件: `").append(change.filePath()).append("`\n\n");
            sb.append(change.incrementalDiff()).append("\n");
        }

        sb.append("---\n\n");
        sb.append("**统计**: 新增 ").append(totalAdditions).append(" 行，删除 ").append(totalDeletions).append(" 行\n");

        if (commitMessages != null && !commitMessages.isEmpty()) {
            sb.append("\n**提交信息**: ").append(commitMessages).append("\n");
        }

        return sb.toString();
    }

    /**
     * 判断是否仅为注释行
     */
    private boolean isCommentOnly(String line) {
        String trimmed = line.trim();
        // 各种语言的注释
        return trimmed.startsWith("//")      // Java, C, JS
            || trimmed.startsWith("#")        // Python, Shell
            || trimmed.startsWith("/*")       // C, Java 多行注释
            || trimmed.startsWith("*")        // JavaDoc
            || trimmed.startsWith("--")       // SQL
            || trimmed.startsWith("<!--");    // XML, HTML
    }

    /**
     * 增量变更记录
     */
    public record IncrementalChange(
        String filePath,
        String incrementalDiff,
        String fullDiff,
        List<CodeLine> addedLines,
        List<CodeLine> deletedLines
    ) {}

    /**
     * 代码行
     */
    public record CodeLine(String content, String type) {}
}
