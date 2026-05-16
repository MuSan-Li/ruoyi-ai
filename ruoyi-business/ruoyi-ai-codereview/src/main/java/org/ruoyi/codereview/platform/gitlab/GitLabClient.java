package org.ruoyi.codereview.platform.gitlab;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.codereview.config.CodeReviewProperties;
import org.ruoyi.codereview.entity.CodeChange;
import org.ruoyi.codereview.platform.GitPlatformClient;
import org.springframework.http.*;
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
public class GitLabClient implements GitPlatformClient {

    private final String apiUrl;
    private final String token;
    private final RestTemplate restTemplate;

    public GitLabClient(CodeReviewProperties.GitLabConfig config, RestTemplate restTemplate) {
        String baseUrl = config.getUrl();
        this.apiUrl = baseUrl.endsWith("/") ? baseUrl + "api/v4" : baseUrl + "/api/v4";
        this.token = config.getToken();
        this.restTemplate = restTemplate;
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
                    return extractCommitMessages(commits);
                } catch (Exception e) {
                    log.warn("获取 MR commits 失败: {}", e.getMessage());
                }
            }
        }

        // Push 事件
        JSONArray commits = payload.getJSONArray("commits");
        return extractCommitMessages(commits);
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
        return attrs.getBool("draft", false) || attrs.getBool("work_in_progress", false);
    }

    // ==================== HTTP 工具方法 ====================

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("PRIVATE-TOKEN", token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private JSONObject doGet(String url) {
        HttpEntity<Void> entity = new HttpEntity<>(buildHeaders());
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            return JSONUtil.parseObj(response.getBody());
        }
        return null;
    }

    private JSONArray doGetArray(String url) {
        HttpEntity<Void> entity = new HttpEntity<>(buildHeaders());
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            String body = response.getBody();
            if (body.startsWith("[")) {
                return JSONUtil.parseArray(body);
            }
        }
        return new JSONArray();
    }

    private void doPost(String url, JSONObject body) {
        HttpEntity<String> entity = new HttpEntity<>(body.toString(), buildHeaders());
        restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
    }

    // ==================== 解析工具 ====================

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

    private List<CodeChange> parseDiffArray(JSONArray diffs) {
        List<CodeChange> result = new ArrayList<>();
        if (diffs == null) return result;

        for (int i = 0; i < diffs.size(); i++) {
            JSONObject diff = diffs.getJSONObject(i);
            CodeChange cc = new CodeChange();
            cc.setFilePath(diff.getStr("new_path", diff.getStr("new_path", "")));
            cc.setFileName(extractFileName(cc.getFilePath()));
            cc.setDiff(diff.getStr("diff", ""));
            cc.setChangeType(detectChangeType(diff));
            cc.setAdditions(countLines(cc.getDiff(), true));
            cc.setDeletions(countLines(cc.getDiff(), false));
            result.add(cc);
        }
        return result;
    }

    private String detectChangeType(JSONObject change) {
        if (change.getBool("deleted_file", false)) return "delete";
        if (change.getBool("new_file", false)) return "add";
        return "modify";
    }

    private int countLines(String diff, boolean additions) {
        if (diff == null || diff.isEmpty()) return 0;
        char prefix = additions ? '+' : '-';
        int count = 0;
        for (String line : diff.split("\n")) {
            if (line.length() > 0 && line.charAt(0) == prefix
                && (line.length() < 2 || line.charAt(1) != prefix)) {
                count++;
            }
        }
        return count;
    }

    private String extractFileName(String path) {
        if (path == null || path.isEmpty()) return "";
        int idx = path.lastIndexOf('/');
        return idx >= 0 ? path.substring(idx + 1) : path;
    }

    private String extractCommitMessages(JSONArray commits) {
        if (commits == null || commits.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < commits.size(); i++) {
            JSONObject commit = commits.getJSONObject(i);
            sb.append("- ").append(commit.getStr("message", commit.getStr("title", ""))).append("\n");
        }
        return sb.toString();
    }
}
