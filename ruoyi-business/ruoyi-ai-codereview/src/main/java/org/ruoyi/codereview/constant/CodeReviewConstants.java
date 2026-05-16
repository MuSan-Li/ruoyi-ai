package org.ruoyi.codereview.constant;

/**
 * 代码审查常量
 */
public final class CodeReviewConstants {

    private CodeReviewConstants() {
    }

    // ==================== 评分等级 ====================

    /** 优秀 - 90分以上 */
    public static final int SCORE_EXCELLENT = 90;

    /** 良好 - 80分以上 */
    public static final int SCORE_GOOD = 80;

    /** 合格 - 70分以上 */
    public static final int SCORE_PASS = 70;

    /** 待改进 - 60分以上 */
    public static final int SCORE_NEEDS_IMPROVEMENT = 60;

    // ==================== 平台标识 ====================

    public static final String PLATFORM_GITHUB = "github";
    public static final String PLATFORM_GITLAB = "gitlab";
    public static final String PLATFORM_GITEA = "gitea";

    // ==================== 事件类型 ====================

    public static final String EVENT_PULL_REQUEST = "pull_request";
    public static final String EVENT_MERGE_REQUEST = "merge_request";
    public static final String EVENT_PUSH = "push";

    // ==================== 审查维度权重 ====================

    /** 功能性与健壮性权重 */
    public static final int WEIGHT_FUNCTIONALITY = 40;

    /** 安全与风险权重 */
    public static final int WEIGHT_SECURITY = 30;

    /** 最佳实践权重 */
    public static final int WEIGHT_BEST_PRACTICE = 20;

    /** 性能与效率权重 */
    public static final int WEIGHT_PERFORMANCE = 5;

    /** Commit Message权重 */
    public static final int WEIGHT_COMMIT_MESSAGE = 5;

    // ==================== 默认配置 ====================

    /** 默认最大 Token 数 */
    public static final int DEFAULT_MAX_TOKENS = 10000;

    /** 默认审查风格 */
    public static final String DEFAULT_REVIEW_STYLE = "professional";

    /** 默认支持的文件扩展名 */
    public static final String DEFAULT_EXTENSIONS = ".c,.cc,.cpp,.cs,.css,.cxx,.go,.h,.hh,.hpp,.hxx,.java,.js,.jsx,.md,.php,.py,.sql,.ts,.tsx,.vue,.yml";

    // ==================== 通知类型 ====================

    public static final String NOTIFY_TYPE_DINGTALK = "dingtalk";
    public static final String NOTIFY_TYPE_WECOM = "wecom";
    public static final String NOTIFY_TYPE_FEISHU = "feishu";
    public static final String NOTIFY_TYPE_WEBHOOK = "webhook";
}
