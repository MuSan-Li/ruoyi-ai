package org.ruoyi.codereview.platform;

import cn.hutool.json.JSONObject;
import org.ruoyi.codereview.entity.CodeChange;

import java.util.List;

/**
 * Git 平台 API 客户端接口
 */
public interface GitPlatformClient {

    /**
     * 平台标识
     */
    String getPlatform();

    /**
     * 获取 MR/PR 的代码变更
     */
    List<CodeChange> fetchMrChanges(JSONObject payload);

    /**
     * 获取 Push 的代码变更
     */
    List<CodeChange> fetchPushChanges(JSONObject payload);

    /**
     * 获取 commit 信息列表
     */
    String fetchCommitMessages(JSONObject payload);

    /**
     * 在 MR/PR 上发表审查评论
     */
    boolean postMrComment(JSONObject payload, String comment);

    /**
     * 在 Push commit 上发表审查评论
     */
    boolean postPushComment(JSONObject payload, String comment);

    /**
     * 检查目标分支是否为受保护分支
     */
    boolean isBranchProtected(JSONObject payload);

    /**
     * 检测是否为 Draft MR/PR
     */
    boolean isDraft(JSONObject payload);
}
