package org.ruoyi.codereview.platform;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.ruoyi.codereview.entity.CodeChange;
import org.ruoyi.codereview.platform.gitlab.GitLabClient;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * GitLabClient 单元测试
 */
@Tag("dev")
class GitLabClientTest {

    private GitLabClient client;
    private RestTemplate mockRestTemplate;

    @BeforeEach
    void setUp() {
        mockRestTemplate = mock(RestTemplate.class);
        client = new GitLabClient("https://gitlab.example.com", "test-token", mockRestTemplate);
    }

    @Test
    @DisplayName("getPlatform - 返回 gitlab")
    void getPlatform_returnsGitlab() {
        assertEquals("gitlab", client.getPlatform());
    }

    @Test
    @DisplayName("isDraft - draft 标志为 true")
    void isDraft_draftFlagTrue() {
        JSONObject payload = new JSONObject();
        JSONObject attrs = new JSONObject();
        attrs.set("draft", true);
        payload.set("object_attributes", attrs);

        assertTrue(client.isDraft(payload));
    }

    @Test
    @DisplayName("isDraft - WIP 标志为 true")
    void isDraft_wipFlagTrue() {
        JSONObject payload = new JSONObject();
        JSONObject attrs = new JSONObject();
        attrs.set("work_in_progress", true);
        payload.set("object_attributes", attrs);

        assertTrue(client.isDraft(payload));
    }

    @Test
    @DisplayName("isDraft - 标题包含 Draft: 前缀")
    void isDraft_titleWithDraftPrefix() {
        JSONObject payload = new JSONObject();
        JSONObject attrs = new JSONObject();
        attrs.set("title", "Draft: WIP feature implementation");
        payload.set("object_attributes", attrs);

        assertTrue(client.isDraft(payload));
    }

    @Test
    @DisplayName("isDraft - 标题包含 [WIP] 前缀")
    void isDraft_titleWithWipBrackets() {
        JSONObject payload = new JSONObject();
        JSONObject attrs = new JSONObject();
        attrs.set("title", "[WIP] work in progress");
        payload.set("object_attributes", attrs);

        assertTrue(client.isDraft(payload));
    }

    @Test
    @DisplayName("isDraft - 非 Draft MR")
    void isDraft_notDraft() {
        JSONObject payload = new JSONObject();
        JSONObject attrs = new JSONObject();
        attrs.set("title", "Feature: Add new functionality");
        attrs.set("draft", false);
        attrs.set("work_in_progress", false);
        payload.set("object_attributes", attrs);

        assertFalse(client.isDraft(payload));
    }

    @Test
    @DisplayName("fetchMrChanges - 正常获取变更")
    void fetchMrChanges_success() {
        // 准备 payload
        JSONObject payload = createMrPayload(123, 456);

        // Mock API 响应
        JSONObject apiResponse = new JSONObject();
        JSONArray changes = new JSONArray();

        JSONObject change1 = new JSONObject();
        change1.set("new_path", "src/main/java/Test.java");
        change1.set("diff", "@@ -1,5 +1,7 @@\n public class Test {\n+    // new line\n }");
        change1.set("new_file", true);
        change1.set("deleted_file", false);
        changes.add(change1);

        apiResponse.set("changes", changes);

        mockGetRequest("https://gitlab.example.com/api/v4/projects/123/merge_requests/456/changes", apiResponse);

        List<CodeChange> result = client.fetchMrChanges(payload);

        assertEquals(1, result.size());
        assertEquals("src/main/java/Test.java", result.get(0).getFilePath());
        assertEquals("add", result.get(0).getChangeType());
    }

    @Test
    @DisplayName("fetchCommitMessages - MR 事件")
    void fetchCommitMessages_mrEvent() {
        JSONObject payload = createMrPayload(123, 456);

        // Mock commits API 响应
        JSONArray commits = new JSONArray();
        JSONObject commit1 = new JSONObject();
        commit1.set("message", "feat: add new feature\n\nDetailed description");
        commits.add(commit1);
        JSONObject commit2 = new JSONObject();
        commit2.set("message", "fix: fix bug");
        commits.add(commit2);

        mockGetArrayRequest("https://gitlab.example.com/api/v4/projects/123/repository/commits?ref_name=main&per_page=20", commits);

        String result = client.fetchCommitMessages(payload);

        assertTrue(result.contains("feat: add new feature"));
        assertTrue(result.contains("fix: fix bug"));
    }

    @Test
    @DisplayName("postMrComment - 发送评论成功")
    void postMrComment_success() {
        JSONObject payload = createMrPayload(123, 456);

        mockPostRequest("https://gitlab.example.com/api/v4/projects/123/merge_requests/456/notes");

        boolean result = client.postMrComment(payload, "## AI Code Review\n\nGood job!");

        assertTrue(result);
    }

    @Test
    @DisplayName("postMrComment - 发送评论失败")
    void postMrComment_failure() {
        JSONObject payload = createMrPayload(123, 456);

        when(mockRestTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenThrow(new RuntimeException("Connection failed"));

        boolean result = client.postMrComment(payload, "Test comment");

        assertFalse(result);
    }

    @Test
    @DisplayName("isBranchProtected - 受保护分支")
    void isBranchProtected_protected() {
        JSONObject payload = createMrPayload(123, 456);

        JSONObject branchInfo = new JSONObject();
        branchInfo.set("protected", true);

        mockGetRequest("https://gitlab.example.com/api/v4/projects/123/repository/branches/main", branchInfo);

        assertTrue(client.isBranchProtected(payload));
    }

    @Test
    @DisplayName("isBranchProtected - 非受保护分支")
    void isBranchProtected_notProtected() {
        JSONObject payload = createMrPayload(123, 456);

        JSONObject branchInfo = new JSONObject();
        branchInfo.set("protected", false);

        mockGetRequest("https://gitlab.example.com/api/v4/projects/123/repository/branches/main", branchInfo);

        assertFalse(client.isBranchProtected(payload));
    }

    // ==================== 辅助方法 ====================

    private JSONObject createMrPayload(int projectId, int mrIid) {
        JSONObject payload = new JSONObject();
        JSONObject project = new JSONObject();
        project.set("id", projectId);
        project.set("name", "test-project");
        payload.set("project", project);

        JSONObject attrs = new JSONObject();
        attrs.set("iid", mrIid);
        attrs.set("target_branch", "main");
        attrs.set("source_branch", "feature");
        payload.set("object_attributes", attrs);

        return payload;
    }

    private void mockGetRequest(String url, JSONObject response) {
        ResponseEntity<String> mockResponse = new ResponseEntity<>(response.toString(), HttpStatus.OK);
        when(mockRestTemplate.exchange(
                eq(url),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(mockResponse);
    }

    private void mockGetArrayRequest(String url, JSONArray response) {
        ResponseEntity<String> mockResponse = new ResponseEntity<>(response.toString(), HttpStatus.OK);
        when(mockRestTemplate.exchange(
                eq(url),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(mockResponse);
    }

    private void mockPostRequest(String url) {
        ResponseEntity<String> mockResponse = new ResponseEntity<>("{}", HttpStatus.OK);
        when(mockRestTemplate.exchange(
                eq(url),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(mockResponse);
    }
}
