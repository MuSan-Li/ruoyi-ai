package org.ruoyi.codereview.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.codereview.notifier.NotifierManager;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 审查事件监听器 - 负责发送通知
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewEventListener {

    private final NotifierManager notifierManager;

    @Async
    @EventListener
    public void handleMergeRequestReviewed(MergeRequestReviewedEvent event) {
        log.info("收到 MR 审查完成事件: 项目={}, 作者={}, 分支={}→{}",
                event.getProjectName(), event.getAuthor(),
                event.getSourceBranch(), event.getTargetBranch());

        // 发送通知
        notifierManager.notifyMergeRequestReviewed(
                event.getProjectName(),
                event.getAuthor(),
                event.getSourceBranch(),
                event.getTargetBranch(),
                event.getUrl(),
                event.getScore(),
                event.getReviewResult()
        );
    }

    @Async
    @EventListener
    public void handlePushReviewed(PushReviewedEvent event) {
        log.info("收到 Push 审查完成事件: 项目={}, 作者={}, 分支={}",
                event.getProjectName(), event.getAuthor(), event.getBranch());

        // 发送通知
        notifierManager.notifyPushReviewed(
                event.getProjectName(),
                event.getAuthor(),
                event.getBranch(),
                event.getScore(),
                event.getReviewResult()
        );
    }

    @Async
    @EventListener
    public void handleReviewFailed(ReviewFailedEvent event) {
        log.warn("收到审查失败事件: 项目={}, 平台={}, 事件类型={}, 错误={}",
                event.getProjectName(), event.getPlatform(),
                event.getEventType(), event.getErrorMessage());

        // 发送失败通知
        notifierManager.notifyReviewFailed(
                event.getProjectName(),
                event.getPlatform(),
                event.getEventType(),
                event.getErrorMessage()
        );
    }
}
