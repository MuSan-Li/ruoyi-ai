package org.ruoyi.codereview.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.ruoyi.codereview.metrics.CodeReviewMetrics;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * 审计日志切面
 * <p>
 * 自动记录标注了 @Auditable 注解的方法的执行信息
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final CodeReviewMetrics metrics;

    @Around("@annotation(auditable)")
    public Object auditOperation(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        String operation = auditable.value();
        String methodName = getMethodName(joinPoint);
        LocalDateTime startTime = LocalDateTime.now();

        // 构建审计日志
        AuditLog.AuditLogBuilder logBuilder = AuditLog.builder()
                .operation(operation)
                .methodName(methodName)
                .timestamp(startTime);

        // 记录参数
        if (auditable.recordParams()) {
            String params = Arrays.toString(joinPoint.getArgs());
            logBuilder.params(maskSensitiveData(params));
        }

        try {
            // 执行方法
            Object result = joinPoint.proceed();

            // 记录成功
            logBuilder.success(true);

            // 记录结果
            if (auditable.recordResult() && result != null) {
                logBuilder.result(maskSensitiveData(result.toString()));
            }

            // 记录审计日志
            log.info("[AUDIT] {}", logBuilder.build());

            return result;
        } catch (Throwable e) {
            // 记录失败
            logBuilder.success(false)
                    .errorMessage(e.getMessage());

            log.warn("[AUDIT] {}", logBuilder.build());

            throw e;
        }
    }

    /**
     * 获取方法名
     */
    private String getMethodName(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return signature.getDeclaringType().getSimpleName() + "." + signature.getName();
    }

    /**
     * 脱敏敏感数据
     */
    private String maskSensitiveData(String data) {
        if (data == null) return "null";

        // 脱敏 Token
        data = data.replaceAll("(?i)(token|secret|password|key)\"?\\s*[:=]\\s*\"?[a-zA-Z0-9_-]{8,}",
                "$1=***MASKED***");

        // 限制长度
        if (data.length() > 500) {
            return data.substring(0, 500) + "...[truncated]";
        }

        return data;
    }

    /**
     * 审计日志内部类
     */
    @lombok.Data
    @lombok.Builder
    public static class AuditLog {
        private String operation;
        private String methodName;
        private LocalDateTime timestamp;
        private String params;
        private boolean success;
        private String result;
        private String errorMessage;

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("operation=").append(operation);
            sb.append(", method=").append(methodName);
            sb.append(", time=").append(timestamp);
            if (params != null) {
                sb.append(", params=").append(params);
            }
            sb.append(", success=").append(success);
            if (result != null) {
                sb.append(", result=").append(result);
            }
            if (errorMessage != null) {
                sb.append(", error=").append(errorMessage);
            }
            return sb.toString();
        }
    }
}
