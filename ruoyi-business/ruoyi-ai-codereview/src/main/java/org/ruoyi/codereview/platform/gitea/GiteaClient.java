package org.ruoyi.codereview.platform.gitea;

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
 * Gitea 平台 API 客户端
 */
@Slf4j
public class GiteaClient implements GitPlatformClient {

    private final String apiUrl;
    private final String token;
    private final RestTemplate restTemplate;

    public GiteaClient(CodeReviewProperties.GiteaConfig config, RestTemplate restTemplate) {
        String baseUrl = config.getUrl();
        this.apiUrl = baseUrl.endsWith("/") ? baseUrl + "api/v1" : baseUrl + "/api/v1";
        this.token = config.getToken();
        this.restTemplate = restTemplate;
    }

    @Override
    public String getPlatform() {
        return "gitea";
    }

    @Override
    @Retryable(
        retryFor = {RestClientException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 10000)
    )
    public List<CodeChange> fetchMrChanges(JSONObject payload) {
        JSONObject repo = payload.getJSONObject("repository");
        JSONObject pr = payload.getJSONObject("pull_request");
        String fullName = repo.getStr("full_name");
        int number = pr.getInt("number");

        String url = apiUrl + "/repos/" + fullName + "/pulls/" + number + "/files";
        JSONArray files = doGetArray(url);
        return parseGiteaFiles(files);
    }

    @Override
    @Retryable(
        retryFor = {RestClientException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 10000)
    )
    public List<CodeChange> fetchPushChanges(JSONObject payload) {
        JSONObject repo = payload.getJSONObject("repository");
        String fullName = repo.getStr("full_name");
        String before = payload.getStr("before");
        String after = payload.getStr("after");

        if ("0000000000000000000000000000000000000000".equals(before)) {
            return new ArrayList<>();
        }

        String url = apiUrl + "/repos/" + fullName + "/compare/" + before + "..." + after;
        JSONObject result = doGet(url);
        if (result == null) return new ArrayList<>();

        JSONArray files = result.getJSONArray("files");
        return parseGiteaFiles(files);
    }

    @Override
    public String fetchCommitMessages(JSONObject payload) {
        JSONObject repo = payload.getJSONObject("repository");
        String fullName = repo.getStr("full_name");

        // PR 事件
        if (payload.containsKey("pull_request")) {
            JSONObject pr = payload.getJSONObject("pull_request");
            String url = apiUrl + "/repos/" + fullName + "/pulls/" + pr.getInt("number") + "/commits";
            try {
                JSONArray commits = doGetArray(url);
                return extractCommitMessages(commits, "commit");
            } catch (Exception e) {
                log.warn("获取 PR commits 失败: {}", e.getMessage());
            }
        }

        // Push 事件
        JSONArray commits = payload.getJSONArray("commits");
        return extractCommitMessages(commits, "message");
    }

    @Override
    public boolean postMrComment(JSONObject payload, String comment) {
        JSONObject repo = payload.getJSONObject("repository");
        JSONObject pr = payload.getJSONObject("pull_request");
        String fullName = repo.getStr("full_name");
        int number = pr.getInt("number");

        String url = apiUrl + "/repos/" + fullName + "/issues/" + number + "/comments";
        JSONObject body = new JSONObject();
        body.set("body", comment);

        try {
            doPost(url, body);
            log.info("Gitea PR 评论发送成功: repo={}, pr={}", fullName, number);
            return true;
        } catch (Exception e) {
            log.error("Gitea PR 评论发送失败: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public boolean postPushComment(JSONObject payload, String comment) {
        String after = payload.getStr("after");
        JSONObject repo = payload.getJSONObject("repository");
        String fullName = repo.getStr("full_name");

        String url = apiUrl + "/repos/" + fullName + "/commits/" + after + "/comments";
        JSONObject body = new JSONObject();
        body.set("body", comment);

        try {
            doPost(url, body);
            log.info("Gitea Push 评论发送成功: repo={}, commit={}", fullName, after);
            return true;
        } catch (Exception e) {
            log.error("Gitea Push 评论发送失败: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public boolean isBranchProtected(JSONObject payload) {
        JSONObject repo = payload.getJSONObject("repository");
        String fullName = repo.getStr("full_name");
        JSONObject pr = payload.getJSONObject("pull_request");
        JSONObject base = pr.getJSONObject("base");
        String branch = base.getStr("ref");

        String url = apiUrl + "/repos/" + fullName + "/branches/" + branch;
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
        JSONObject pr = payload.getJSONObject("pull_request");
        if (pr == null) return false;
        return pr.getBool("draft", false);
    }

    // ==================== HTTP 工具方法 ====================

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "token " + token);
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

    private List<CodeChange> parseGiteaFiles(JSONArray files) {
        List<CodeChange> result = new ArrayList<>();
        if (files == null) return result;

        for (int i = 0; i < files.size(); i++) {
            JSONObject file = files.getJSONObject(i);
            String status = file.getStr("status", "modified");
            if ("deleted".equals(status)) continue;

            CodeChange cc = new CodeChange();
            cc.setFilePath(file.getStr("filename", ""));
            cc.setFileName(extractFileName(cc.getFilePath()));
            cc.setChangeType("added".equals(status) ? "add" : "modified".equals(status) ? "modify" : "rename");
            cc.setAdditions(file.getInt("additions", 0));
            cc.setDeletions(file.getInt("deletions", 0));
            cc.setDiff(file.getStr("patch", file.getStr("diff", "")));
            result.add(cc);
        }
        return result;
    }

    private String extractFileName(String path) {
        if (path == null || path.isEmpty()) return "";
        int idx = path.lastIndexOf('/');
        return idx >= 0 ? path.substring(idx + 1) : path;
    }

    private String extractCommitMessages(JSONArray commits, String messageField) {
        if (commits == null || commits.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < commits.size(); i++) {
            JSONObject commit = commits.getJSONObject(i);
            String msg;
            if ("commit".equals(messageField)) {
                JSONObject commitObj = commit.getJSONObject("commit");
                msg = commitObj != null ? commitObj.getStr("message", "") : "";
            } else {
                msg = commit.getStr(messageField, "");
            }
            sb.append("- ").append(msg.split("\n")[0]).append("\n");
        }
        return sb.toString();
    }
}
