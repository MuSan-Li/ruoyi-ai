package org.ruoyi.codereview.platform.gitlab;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.codereview.config.CodeReviewProperties;
import org.ruoyi.codereview.entity.CodeChange;
import org.ruoyi.codereview.platform.AbstractPlatformClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * GitLab 平台 API 客户端
 */
@Slf4j
public class GitLabClient extends AbstractPlatformClient {

    /**
     * 从配置对象创建（兼容旧方式）
     */
    public GitLabClient(CodeReviewProperties.GitLabConfig config, RestTemplate restTemplate) {
        super(buildApiUrl(config.getUrl()), config.getToken(), restTemplate);
    }

    /**
     * 直接指定 URL 和 Token 创建（动态配置）
     */
    public GitLabClient(String url, String token, RestTemplate restTemplate) {
        super(buildApiUrl(url), token, restTemplate);
    }

    private static String buildApiUrl(String baseUrl) {
        return baseUrl.endsWith("/") ? baseUrl + "api/v4" : baseUrl + "/api/v4";
    }

    @Override
    protected HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("PRIVATE-TOKEN", token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @Override
    public String getPlatform() {
        return "gitlab";
    }

    @Override
    @Retryable(
        retryFor = {RestClientException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 10000)
    )
    public List<CodeChange> fetchMrChanges(JSONObject payload) {
        JSONObject project = payload.getJSONObject("project");
        JSONObject attrs = payload.getJSONObject("object_attributes");
        String projectId = String.valueOf(project.getInt("id"));
        String iid = String.valueOf(attrs.getInt("iid"));

        String url = apiUrl + "/projects/" + projectId + "/merge_requests/" + iid + "/changes";
        JSONObject result = doGet(url);
        if (result == null) return new ArrayList<>();

        JSONArray changes = result.getJSONArray("changes");
        return parseChanges(changes);
    }

    @Override
    @Retryable(
        retryFor = {RestClientException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 10000)
    )
    public List<CodeChange> fetchPushChanges(JSONObject payload) {
        JSONObject project = payload.getJSONObject("project");
        String projectId = String.valueOf(project.getInt("id"));
        String before = payload.getStr("before");
        String after = payload.getStr("after");

        // 新分支：用 commit diff API
        if ("0000000000000000000000000000000000000000".equals(before)) {
            String commitUrl = apiUrl + "/projects/" + projectId + "/repository/commits/" + after + "/diff";
            JSONArray diffs = doGetArray(commitUrl);
            return parseDiffArray(diffs);
        }

        // 正常 push：用 compare API
        String url = apiUrl + "/projects/" + projectId + "/repository/compare?from=" + before + "&to=" + after;
        JSONObject result = doGet(url);
        if (result == null) return new ArrayList<>();

        JSONArray diffs = result.getJSONArray("diffs");
        return parseDiffArray(diffs);
    }

    @Override
    public String fetchCommitMessages(JSONObject payload) {
        JSONObject project = payload.getJSONObject("project");
        String projectId = String.valueOf(project.getInt("id"));

        // MR 事件：从 commits 数组获取
        if (payload.containsKey("object_attributes")) {
            JSONObject attrs = payload.getJSONObject("object_attributes");
            String targetBranch = attrs.getStr("target_branch");
            if (targetBranch != null) {
                String url = apiUrl + "/projects/" + projectId + "/repository/commits?ref_name=" + targetBranch + "&per_page=20";
                try {
                    JSONArray commits = doGetArray(url);
                    return extractCommitMessagesSimple(commits);
                } catch (Exception e) {
                    log.warn("获取 MR commits 失败: {}", e.getMessage());
                }
            }
        }

        // Push 事件
        JSONArray commits = payload.getJSONArray("commits");
        return extractCommitMessagesSimple(commits);
    }

    @Override
    public boolean postMrComment(JSONObject payload, String comment) {
        JSONObject project = payload.getJSONObject("project");
        JSONObject attrs = payload.getJSONObject("object_attributes");
        String projectId = String.valueOf(project.getInt("id"));
        String iid = String.valueOf(attrs.getInt("iid"));

        String url = apiUrl + "/projects/" + projectId + "/merge_requests/" + iid + "/notes";
        JSONObject body = new JSONObject();
        body.set("body", comment);

        try {
            doPost(url, body);
            log.info("GitLab MR 评论发送成功: project={}, mr={}", projectId, iid);
            return true;
        } catch (Exception e) {
            log.error("GitLab MR 评论发送失败: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public boolean postPushComment(JSONObject payload, String comment) {
        String after = payload.getStr("after");
        JSONObject project = payload.getJSONObject("project");
        String projectId = String.valueOf(project.getInt("id"));

        String url = apiUrl + "/projects/" + projectId + "/repository/commits/" + after + "/comments";
        JSONObject body = new JSONObject();
        body.set("note", comment);

        try {
            doPost(url, body);
            log.info("GitLab Push 评论发送成功: project={}, commit={}", projectId, after);
            return true;
        } catch (Exception e) {
            log.error("GitLab Push 评论发送失败: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public boolean isBranchProtected(JSONObject payload) {
        JSONObject project = payload.getJSONObject("project");
        String projectId = String.valueOf(project.getInt("id"));
        JSONObject attrs = payload.getJSONObject("object_attributes");
        String targetBranch = attrs.getStr("target_branch");

        String url = apiUrl + "/projects/" + projectId + "/repository/branches/" + targetBranch;
        try {
            JSONObject result = doGet(url);
            return result != null && result.getBool("protected", false);
        } catch (Exception e) {
            log.warn("检查受保护分支失败: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public boolean isDraft(JSONObject payload) {
        JSONObject attrs = payload.getJSONObject("object_attributes");
        if (attrs == null) return false;

        // 检查 draft 标志
        if (attrs.getBool("draft", false) || attrs.getBool("work_in_progress", false)) {
            return true;
        }

        // 检查标题前缀（GitLab 支持 Draft:/WIP: 前缀）
        String title = attrs.getStr("title", "");
        String lowerTitle = title.toLowerCase().trim();
        return lowerTitle.startsWith("draft:")
            || lowerTitle.startsWith("wip:")
            || lowerTitle.startsWith("[draft]")
            || lowerTitle.startsWith("[wip]")
            || lowerTitle.startsWith("(draft)")
            || lowerTitle.startsWith("(wip)");
    }

    // ==================== GitLab 特有解析方法 ====================

    private List<CodeChange> parseChanges(JSONArray changes) {
        List<CodeChange> result = new ArrayList<>();
        if (changes == null) return result;

        for (int i = 0; i < changes.size(); i++) {
            JSONObject change = changes.getJSONObject(i);
            CodeChange cc = new CodeChange();
            cc.setFilePath(change.getStr("new_path", ""));
            cc.setFileName(extractFileName(cc.getFilePath()));
            cc.setDiff(change.getStr("diff", ""));
            cc.setChangeType(detectChangeType(change));
            cc.setAdditions(countLines(cc.getDiff(), true));
            cc.setDeletions(countLines(cc.getDiff(), false));
            result.add(cc);
        }
        return result;
    }
}
