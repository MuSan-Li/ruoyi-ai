package org.ruoyi.codereview.exception;

import lombok.Getter;

/**
 * 代码审查错误码枚举
 * <p>
 * 错误码规范：CR + 3位数字
 * - CR0xx: 配置相关错误
 * - CR1xx: 平台 API 相关错误
 * - CR2xx: LLM 相关错误
 * - CR3xx: 验证相关错误
 * - CR4xx: 业务逻辑错误
 */
@Getter
public enum CodeReviewErrorCode {

    // ==================== 配置相关错误 (CR0xx) ====================

    /** 配置未找到 */
    CONFIG_NOT_FOUND("CR001", "配置未找到"),
    /** 模型未配置 */
    MODEL_NOT_CONFIGURED("CR002", "模型未配置"),
    /** 平台未配置 */
    PLATFORM_NOT_CONFIGURED("CR003", "平台未配置"),
    /** 无效的配置 */
    INVALID_CONFIG("CR004", "无效的配置"),

    // ==================== 平台 API 相关错误 (CR1xx) ====================

    /** 平台不支持 */
    PLATFORM_NOT_SUPPORTED("CR101", "平台不支持"),
    /** 平台 API 调用失败 */
    PLATFORM_API_ERROR("CR102", "平台 API 调用失败"),
    /** Webhook 签名验证失败 */
    WEBHOOK_SIGNATURE_INVALID("CR103", "Webhook 签名验证失败"),
    /** Token 无效 */
    TOKEN_INVALID("CR104", "Token 无效"),
    /** 获取代码变更失败 */
    FETCH_CHANGES_FAILED("CR105", "获取代码变更失败"),
    /** 发送评论失败 */
    POST_COMMENT_FAILED("CR106", "发送评论失败"),

    // ==================== LLM 相关错误 (CR2xx) ====================

    /** LLM 调用失败 */
    LLM_CALL_FAILED("CR201", "LLM 调用失败"),
    /** 模型未找到 */
    MODEL_NOT_FOUND("CR202", "模型未找到"),
    /** Token 超限 */
    TOKEN_LIMIT_EXCEEDED("CR203", "Token 超限"),
    /** 响应解析失败 */
    RESPONSE_PARSE_FAILED("CR204", "响应解析失败"),

    // ==================== 验证相关错误 (CR3xx) ====================

    /** 参数验证失败 */
    VALIDATION_ERROR("CR301", "参数验证失败"),
    /** 请求体过大 */
    PAYLOAD_TOO_LARGE("CR302", "请求体过大"),
    /** 无效的 JSON 格式 */
    INVALID_JSON("CR303", "无效的 JSON 格式"),
    /** 缺少必要字段 */
    MISSING_REQUIRED_FIELD("CR304", "缺少必要字段"),
    /** 请求频率超限 */
    RATE_LIMIT_EXCEEDED("CR305", "请求频率超限"),

    // ==================== 业务逻辑错误 (CR4xx) ====================

    /** 审查已存在 */
    REVIEW_ALREADY_EXISTS("CR401", "审查已存在"),
    /** 审查被禁用 */
    REVIEW_DISABLED("CR402", "审查被禁用"),
    /** Draft MR 跳过 */
    DRAFT_MR_SKIPPED("CR403", "Draft MR 跳过审查"),
    /** 非受保护分支跳过 */
    UNPROTECTED_BRANCH_SKIPPED("CR404", "非受保护分支跳过审查");

    private final String code;
    private final String message;

    CodeReviewErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    /**
     * 根据错误码查找枚举
     */
    public static CodeReviewErrorCode fromCode(String code) {
        for (CodeReviewErrorCode errorCode : values()) {
            if (errorCode.getCode().equals(code)) {
                return errorCode;
            }
        }
        return null;
    }
}
