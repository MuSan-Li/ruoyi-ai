package org.ruoyi.codereview.common;

/**
 * 代码审查常量定义
 * <p>
 * 统一管理模块内的常量值，避免魔法值散落在代码各处
 */
public final class CodeReviewConstants {

    private CodeReviewConstants() {
        // 工具类，禁止实例化
    }

    // ==================== HTTP 配置常量 ====================

    /** HTTP 连接超时时间（毫秒） */
    public static final int HTTP_CONNECT_TIMEOUT_MS = 30_000;

    /** HTTP 读取超时时间（毫秒） */
    public static final int HTTP_READ_TIMEOUT_MS = 120_000;

    /** HTTP 连接池最大连接数 */
    public static final int HTTP_CONNECTION_POOL_MAX = 100;

    /** HTTP 连接池每路由最大连接数 */
    public static final int HTTP_CONNECTION_POOL_PER_ROUTE = 20;

    /** HTTP 连接存活时间（秒） */
    public static final long HTTP_CONNECTION_TTL_SECONDS = 60;

    /** Webhook 请求超时时间（毫秒） */
    public static final int WEBHOOK_TIMEOUT_MS = 30_000;

    /** 最大请求体大小（字节）- 1MB */
    public static final int MAX_PAYLOAD_SIZE_BYTES = 1024 * 1024;

    // ==================== 审查配置常量 ====================

    /** 默认最大 Token 数 */
    public static final int DEFAULT_MAX_TOKENS = 10000;

    /** 默认通过分数 */
    public static final int DEFAULT_PASS_SCORE = 60;

    /** 默认最大审查文件数 */
    public static final int DEFAULT_MAX_FILES = 50;

    /** 默认审查风格 */
    public static final String DEFAULT_REVIEW_STYLE = "professional";

    /** 支持的文件扩展名 */
    public static final String DEFAULT_FILE_EXTENSIONS = ".java,.js,.ts,.py,.go,.rs,.cpp,.c,.h,.vue,.jsx,.tsx,.sql,.xml,.yaml,.yml,.json,.properties";

    // ==================== 线程池配置常量 ====================

    /** 异步任务核心线程数 */
    public static final int ASYNC_CORE_POOL_SIZE = 4;

    /** 异步任务最大线程数 */
    public static final int ASYNC_MAX_POOL_SIZE = 16;

    /** 异步任务队列容量 */
    public static final int ASYNC_QUEUE_CAPACITY = 100;

    /** 异步任务线程空闲时间（秒） */
    public static final int ASYNC_KEEP_ALIVE_SECONDS = 60;

    /** 异步任务线程名前缀 */
    public static final String ASYNC_THREAD_NAME_PREFIX = "code-review-";

    // ==================== 评分维度常量 ====================

    /** 功能性权重 */
    public static final int WEIGHT_FUNCTIONALITY = 40;

    /** 安全性权重 */
    public static final int WEIGHT_SECURITY = 30;

    /** 最佳实践权重 */
    public static final int WEIGHT_BEST_PRACTICE = 20;

    /** 性能权重 */
    public static final int WEIGHT_PERFORMANCE = 5;

    /** Commit Message 权重 */
    public static final int WEIGHT_COMMIT_MESSAGE = 5;

    /** 总分 */
    public static final int TOTAL_SCORE = 100;

    // ==================== 平台相关常量 ====================

    /** GitLab 平台标识 */
    public static final String PLATFORM_GITLAB = "gitlab";

    /** GitHub 平台标识 */
    public static final String PLATFORM_GITHUB = "github";

    /** Gitea 平台标识 */
    public static final String PLATFORM_GITEA = "gitea";

    /** 新分支标识（全零 hash） */
    public static final String NEW_BRANCH_HASH = "0000000000000000000000000000000000000000";

    // ==================== 事件类型常量 ====================

    /** MR/PR 事件类型 */
    public static final String EVENT_TYPE_MR = "mr";

    /** Push 事件类型 */
    public static final String EVENT_TYPE_PUSH = "push";

    // ==================== 缓存配置常量 ====================

    /** 缓存刷新间隔（毫秒）- 5分钟 */
    public static final long CACHE_REFRESH_INTERVAL_MS = 300_000;

    /** 缓存懒加载双检锁等待时间（毫秒） */
    public static final long CACHE_LOCK_WAIT_MS = 100;

    // ==================== 重试配置常量 ====================

    /** 重试最大次数 */
    public static final int RETRY_MAX_ATTEMPTS = 3;

    /** 重试初始延迟（毫秒） */
    public static final long RETRY_INITIAL_DELAY_MS = 2000;

    /** 重试延迟乘数 */
    public static final double RETRY_MULTIPLIER = 2.0;

    // ==================== 审计日志常量 ====================

    /** 审计操作：创建配置 */
    public static final String AUDIT_OP_CONFIG_CREATE = "CONFIG_CREATE";

    /** 审计操作：更新配置 */
    public static final String AUDIT_OP_CONFIG_UPDATE = "CONFIG_UPDATE";

    /** 审计操作：删除配置 */
    public static final String AUDIT_OP_CONFIG_DELETE = "CONFIG_DELETE";

    /** 审计操作：执行审查 */
    public static final String AUDIT_OP_REVIEW_EXECUTE = "REVIEW_EXECUTE";

    /** 审计操作：发送通知 */
    public static final String AUDIT_OP_NOTIFY_SEND = "NOTIFY_SEND";
}
