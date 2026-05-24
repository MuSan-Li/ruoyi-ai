package org.ruoyi.codereview.platform;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.codereview.entity.CodeChange;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * 平台客户端抽象基类
 * <p>
 * 提取各平台客户端的公共逻辑：
 * - 公共字段（apiUrl, token, restTemplate）
 * - HTTP 工具方法（doGet, doGetArray, doPost）
 * - 通用解析方法（extractFileName, extractCommitMessages）
 * <p>
 * 子类只需实现 buildHeaders() 和平台特有的解析逻辑
 */
@Slf4j
public abstract class AbstractPlatformClient implements GitPlatformClient {

    private static final ObjectMapper JACKSON = new ObjectMapper();

    protected final String apiUrl;
    protected final String token;
    protected final RestTemplate restTemplate;

    protected AbstractPlatformClient(String apiUrl, String token, RestTemplate restTemplate) {
        this.apiUrl = apiUrl;
        this.token = token;
        this.restTemplate = restTemplate;
    }

    // ==================== 抽象方法 ====================

    /**
     * 构建请求头（不同平台认证方式不同）
     */
    protected abstract HttpHeaders buildHeaders();

    // ==================== HTTP 工具方法 ====================

    protected JSONObject doGet(String url) {
        HttpEntity<Void> entity = new HttpEntity<>(buildHeaders());
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            return parseWithJackson(response.getBody());
        }
        return null;
    }

    protected JSONArray doGetArray(String url) {
        HttpEntity<Void> entity = new HttpEntity<>(buildHeaders());
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            String body = response.getBody();
            if (body.startsWith("[")) {
                return parseArrayWithJackson(body);
            }
        }
        return new JSONArray();
    }

    /**
     * 使用 Jackson 解析 JSON 字符串为 hutool JSONObject
     * Jackson 对复杂 JSON（含特殊字符、长字符串）的兼容性更好
     */
    private JSONObject parseWithJackson(String json) {
        try {
            JsonNode node = JACKSON.readTree(json);
            return JSONUtil.parseObj(JACKSON.writeValueAsString(node));
        } catch (Exception e) {
            log.warn("Jackson 解析失败，回退到 hutool: {}", e.getMessage());
            return JSONUtil.parseObj(json);
        }
    }

    private JSONArray parseArrayWithJackson(String json) {
        try {
            JsonNode node = JACKSON.readTree(json);
            return JSONUtil.parseArray(JACKSON.writeValueAsString(node));
        } catch (Exception e) {
            log.warn("Jackson 解析数组失败，回退到 hutool: {}", e.getMessage());
            return JSONUtil.parseArray(json);
        }
    }

    protected void doPost(String url, JSONObject body) {
        HttpEntity<String> entity = new HttpEntity<>(body.toString(), buildHeaders());
        restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
    }

    // ==================== 通用解析工具 ====================

    /**
     * 从文件路径中提取文件名
     */
    protected String extractFileName(String path) {
        if (path == null || path.isEmpty()) return "";
        int idx = path.lastIndexOf('/');
        return idx >= 0 ? path.substring(idx + 1) : path;
    }

    /**
     * 提取 commit 消息（通用格式，支持不同字段名）
     *
     * @param commits      commit 数组
     * @param messageField 消息字段名（"message" 或 "commit"）
     */
    protected String extractCommitMessages(JSONArray commits, String messageField) {
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

    /**
     * 简化版 commit 消息提取（直接取 message 或 title 字段）
     */
    protected String extractCommitMessagesSimple(JSONArray commits) {
        if (commits == null || commits.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < commits.size(); i++) {
            JSONObject commit = commits.getJSONObject(i);
            sb.append("- ").append(commit.getStr("message", commit.getStr("title", ""))).append("\n");
        }
        return sb.toString();
    }

    /**
     * 统计 diff 中的新增/删除行数
     */
    protected int countLines(String diff, boolean additions) {
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

    /**
     * 检测变更类型（基于 new_file / deleted_file 标志）
     */
    protected String detectChangeType(JSONObject change) {
        if (change.getBool("deleted_file", false)) return "delete";
        if (change.getBool("new_file", false)) return "add";
        return "modify";
    }

    /**
     * 解析 diff 格式的变更数组（GitLab 风格）
     */
    protected List<CodeChange> parseDiffArray(JSONArray diffs) {
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
}
