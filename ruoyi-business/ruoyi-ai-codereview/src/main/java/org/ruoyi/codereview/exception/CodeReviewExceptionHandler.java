package org.ruoyi.codereview.exception;

import lombok.extern.slf4j.Slf4j;
import org.ruoyi.common.core.domain.R;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 代码审查全局异常处理
 */
@Slf4j
@RestControllerAdvice(basePackages = "org.ruoyi.codereview")
public class CodeReviewExceptionHandler {

    @ExceptionHandler(CodeReviewException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleCodeReviewException(CodeReviewException e) {
        log.error("代码审查异常: code={}, platform={}, eventType={}, message={}",
            e.getErrorCode(), e.getPlatform(), e.getEventType(), e.getMessage());
        return R.fail(e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleIllegalArgumentException(IllegalArgumentException e) {
        log.error("参数错误: {}", e.getMessage());
        return R.fail("参数错误: " + e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return R.fail("系统异常，请联系管理员");
    }
}
