package org.ruoyi.codereview.util;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.ruoyi.codereview.entity.CodeChange;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PromptTemplate 单元测试
 */
@Tag("dev")
class PromptTemplateTest {

    @Test
    void buildSystemPrompt_professionalStyle_containsBasePrompt() {
        String prompt = PromptTemplate.buildSystemPrompt("professional");
        assertTrue(prompt.contains("资深软件工程师"));
        assertTrue(prompt.contains("专业严谨"));
    }

    @Test
    void buildSystemPrompt_sarcasticStyle_containsStyleHint() {
        String prompt = PromptTemplate.buildSystemPrompt("sarcastic");
        assertTrue(prompt.contains("幽默讽刺"));
    }

    @Test
    void buildSystemPrompt_gentleStyle_containsStyleHint() {
        String prompt = PromptTemplate.buildSystemPrompt("gentle");
        assertTrue(prompt.contains("温和友善"));
    }

    @Test
    void buildSystemPrompt_humorousStyle_containsStyleHint() {
        String prompt = PromptTemplate.buildSystemPrompt("humorous");
        assertTrue(prompt.contains("轻松幽默"));
    }

    @Test
    void buildSystemPrompt_unknownStyle_defaultsToProfessional() {
        String prompt = PromptTemplate.buildSystemPrompt("unknown");
        assertTrue(prompt.contains("专业严谨"));
    }

    @Test
    void buildUserPrompt_emptyChanges_returnsHeader() {
        String prompt = PromptTemplate.buildUserPrompt(List.of(), null);
        assertTrue(prompt.contains("请审查以下代码变更"));
    }

    @Test
    void buildUserPrompt_withChanges_includesFileInfo() {
        CodeChange change = new CodeChange();
        change.setFilePath("src/Test.java");
        change.setChangeType("modify");
        change.setAdditions(10);
        change.setDeletions(5);
        change.setDiff("+new code\n-old code");

        String prompt = PromptTemplate.buildUserPrompt(List.of(change), null);

        assertTrue(prompt.contains("src/Test.java"));
        assertTrue(prompt.contains("modify"));
        assertTrue(prompt.contains("+10"));
        assertTrue(prompt.contains("-5"));
        assertTrue(prompt.contains("new code"));
    }

    @Test
    void buildUserPrompt_withCommitMessages_includesMessages() {
        String prompt = PromptTemplate.buildUserPrompt(List.of(), "fix: 修复bug");
        assertTrue(prompt.contains("Commit 信息"));
        assertTrue(prompt.contains("fix: 修复bug"));
    }

    @Test
    void extractScore_nullInput_returnsDefault() {
        assertEquals(70, PromptTemplate.extractScore(null));
    }

    @Test
    void extractScore_withMarkdownFormat_extractsCorrectly() {
        String result = "审查结果...\n\n**总分：85分**\n\n建议...";
        assertEquals(85, PromptTemplate.extractScore(result));
    }

    @Test
    void extractScore_withColonFormat_extractsCorrectly() {
        String result = "审查结果...\n\n**总分:92分**\n\n建议...";
        assertEquals(92, PromptTemplate.extractScore(result));
    }

    @Test
    void extractScore_withJsonFormat_extractsCorrectly() {
        String result = "{\"review\": \"...\", \"score\": 88}";
        assertEquals(88, PromptTemplate.extractScore(result));
    }

    @Test
    void extractScore_noScore_returnsDefault() {
        String result = "审查结果，没有分数";
        assertEquals(70, PromptTemplate.extractScore(result));
    }
}
