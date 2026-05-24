package org.ruoyi.codereview.platform.github;

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
 * GitHub 平台 API 客户端
 */
@Slf4j
public class GitHubClient extends AbstractPlatformClient {

    /**
     * 从配置对象创建（兼容旧方式）
     */
    public GitHubClient(CodeReviewProperties.GitHubConfig config, RestTemplate restTemplate) {
        super(config.getUrl(), config.getToken(), restTemplate);
    }

    /**
     * 直接指定 URL 和 Token 创建（动态配置）
     */
    public GitHubClient(String url, String token, RestTemplate restTemplate) {
        super(url, token, restTemplate);
    }

    @Override
    protected HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "token " + token);
        headers.set("Accept", "application/vnd.github.v3+json");
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @Override
    public String getPlatform() {
        return "github";
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

        String url = apiUrl + "/repos/" + fullName + "/pulls/" + number + "/files?per_page=100";
        JSONArray files = doGetArray(url);
        return parseGitHubFiles(files);
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

        // 新分支：尝试获取最新 commit 的 diff
        if ("0000000000000000000000000000000000000000".equals(before)) {
            log.info("GitHub 新分支推送，获取最新 commit diff: {}", after);
            try {
                // 获取单个 commit 的文件变更
                String commitUrl = apiUrl + "/repos/" + fullName + "/commits/" + after;
                JSONObject commitInfo = doGet(commitUrl);
                if (commitInfo != null) {
                    JSONArray files = commitInfo.getJSONArray("files");
                    return parseGitHubFiles(files);
                }
            } catch (Exception e) {
                log.warn("获取新分支 commit diff 失败: {}", e.getMessage());
            }
            return new ArrayList<>();
        }

        String url = apiUrl + "/repos/" + fullName + "/compare/" + before + "..." + after;
        JSONObject result = doGet(url);
        if (result == null) return new ArrayList<>();

        JSONArray files = result.getJSONArray("files");
        return parseGitHubFiles(files);
    }

    @Override
    public String fetchCommitMessages(JSONObject payload) {
        JSONObject repo = payload.getJSONObject("repository");
        String fullName = repo.getStr("full_name");

        // PR 事件
        if (payload.containsKey("pull_request")) {
            JSONObject pr = payload.getJSONObject("pull_request");
            String url = apiUrl + "/repos/" + fullName + "/pulls/" + pr.getInt("number") + "/commits?per_page=20";
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
            log.info("GitHub PR 评论发送成功: repo={}, pr={}", fullName, number);
            return true;
        } catch (Exception e) {
            log.error("GitHub PR 评论发送失败: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public boolean postPushComment(JSONObject payload, String comment) {
        JSONArray commits = payload.getJSONArray("commits");
        if (commits == null || commits.isEmpty()) return false;

        JSONObject repo = payload.getJSONObject("repository");
        String fullName = repo.getStr("full_name");
        String headCommitId = payload.getStr("after");

        String url = apiUrl + "/repos/" + fullName + "/commits/" + headCommitId + "/comments";
        JSONObject body = new JSONObject();
        body.set("body", comment);

        try {
            doPost(url, body);
            log.info("GitHub Push 评论发送成功: repo={}, commit={}", fullName, headCommitId);
            return true;
        } catch (Exception e) {
            log.error("GitHub Push 评论发送失败: {}", e.getMessage());
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

    // ==================== GitHub 特有解析方法 ====================

    private List<CodeChange> parseGitHubFiles(JSONArray files) {
        List<CodeChange> result = new ArrayList<>();
        if (files == null) return result;

        for (int i = 0; i < files.size(); i++) {
            JSONObject file = files.getJSONObject(i);
            String status = file.getStr("status", "modified");
            if ("removed".equals(status)) continue;

            CodeChange cc = new CodeChange();
            cc.setFilePath(file.getStr("filename", ""));
            cc.setFileName(extractFileName(cc.getFilePath()));
            cc.setChangeType("added".equals(status) ? "add" : "modified".equals(status) ? "modify" : "rename");
            cc.setAdditions(file.getInt("additions", 0));
            cc.setDeletions(file.getInt("deletions", 0));
            cc.setDiff(file.getStr("patch", ""));
            result.add(cc);
        }
        return result;
    }
}
