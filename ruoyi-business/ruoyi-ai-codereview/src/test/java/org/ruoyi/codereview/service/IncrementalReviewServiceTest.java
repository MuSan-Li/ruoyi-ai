package org.ruoyi.codereview.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.ruoyi.codereview.entity.CodeChange;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 增量审查服务测试
 */
@Tag("unit")
class IncrementalReviewServiceTest {

    private IncrementalReviewService service;

    @BeforeEach
    void setUp() {
        service = new IncrementalReviewService();
    }

    @Test
    @DisplayName("提取新增代码行")
    void testExtractAddedLines() {
        CodeChange change = new CodeChange();
        change.setFilePath("test.java");
        change.setDiff("""
            @@ -1,3 +1,5 @@
             public class Test {
            +    private String name;
            +    private int age;
                 public void run() {}
             }
            """);

        var result = service.extractChangedLines(change);

        assertEquals("test.java", result.filePath());
        assertEquals(2, result.addedLines().size());
        assertTrue(result.incrementalDiff().contains("新增代码"));
    }

    @Test
    @DisplayName("提取删除代码行")
    void testExtractDeletedLines() {
        CodeChange change = new CodeChange();
        change.setFilePath("test.py");
        change.setDiff("""
            @@ -1,4 +1,2 @@
            -import os
            -import sys
             def main():
                 pass
            """);

        var result = service.extractChangedLines(change);

        assertEquals(2, result.deletedLines().size());
        assertTrue(result.incrementalDiff().contains("删除代码"));
    }

    @Test
    @DisplayName("忽略纯注释行")
    void testIgnoreCommentLines() {
        CodeChange change = new CodeChange();
        change.setFilePath("test.java");
        change.setDiff("""
            @@ -1,2 +1,4 @@
             public class Test {
            +    // This is a comment
            +    /* Block comment */
            +    private int value;
             }
            """);

        var result = service.extractChangedLines(change);

        // 只保留非注释行
        assertEquals(1, result.addedLines().size());
        assertEquals("private int value;", result.addedLines().get(0).content());
    }

    @Test
    @DisplayName("构建增量审查提示")
    void testBuildIncrementalPrompt() {
        CodeChange change1 = new CodeChange();
        change1.setFilePath("file1.java");
        change1.setDiff("+ added line\n- deleted line");

        CodeChange change2 = new CodeChange();
        change2.setFilePath("file2.py");
        change2.setDiff("+ another line");

        var changes = service.extractChangedLines(List.of(change1, change2));
        String prompt = service.buildIncrementalPrompt(changes, "feat: test commit");

        assertTrue(prompt.contains("代码变更审查"));
        assertTrue(prompt.contains("file1.java"));
        assertTrue(prompt.contains("file2.py"));
        assertTrue(prompt.contains("feat: test commit"));
    }

    @Test
    @DisplayName("空 diff 处理")
    void testEmptyDiff() {
        CodeChange change = new CodeChange();
        change.setFilePath("empty.txt");
        change.setDiff("");

        var result = service.extractChangedLines(change);

        assertEquals(0, result.addedLines().size());
        assertEquals(0, result.deletedLines().size());
    }
}
