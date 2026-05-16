package org.ruoyi.codereview.exception;

/**
 * 代码审查异常
 */
public class CodeReviewException extends RuntimeException {

    private final String errorCode;
    private final String platform;
    private final String eventType;

    public CodeReviewException(String message) {
        super(message);
        this.errorCode = "REVIEW_ERROR";
        this.platform = null;
        this.eventType = null;
    }

    public CodeReviewException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.platform = null;
        this.eventType = null;
    }

    public CodeReviewException(String errorCode, String message, String platform, String eventType) {
        super(message);
        this.errorCode = errorCode;
        this.platform = platform;
        this.eventType = eventType;
    }

    public CodeReviewException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "REVIEW_ERROR";
        this.platform = null;
        this.eventType = null;
    }

    public CodeReviewException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.platform = null;
        this.eventType = null;
    }

    public CodeReviewException(String errorCode, String message, String platform, String eventType, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.platform = platform;
        this.eventType = eventType;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getPlatform() {
        return platform;
    }

    public String getEventType() {
        return eventType;
    }

    // ==================== 预定义异常 ====================

    public static CodeReviewException platformNotSupported(String platform) {
        return new CodeReviewException("PLATFORM_NOT_SUPPORTED", "不支持的平台: " + platform, platform, null);
    }

    public static CodeReviewException modelNotFound(String modelName) {
        return new CodeReviewException("MODEL_NOT_FOUND", "模型未找到: " + modelName + "，请在模型管理中配置该模型", null, null);
    }

    public static CodeReviewException webhookSignatureInvalid(String platform) {
        return new CodeReviewException("WEBHOOK_SIGNATURE_INVALID", "Webhook 签名验证失败", platform, null);
    }

    public static CodeReviewException llmCallFailed(String message, Throwable cause) {
        return new CodeReviewException("LLM_CALL_FAILED", "LLM 调用失败: " + message, null, null, cause);
    }

    public static CodeReviewException platformApiError(String platform, String message, Throwable cause) {
        return new CodeReviewException("PLATFORM_API_ERROR", "平台 API 调用失败: " + message, platform, null, cause);
    }
}
