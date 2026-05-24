package org.ruoyi.codereview.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Push 审查完成事件
 */
@Getter
public class PushReviewedEvent extends ApplicationEvent {

    private final String projectName;
    private final String platform;
    private final String author;
    private final String branch;
    private final int score;
    private final String reviewResult;
    private final String notificationChannels;

    /**
     * 兼容旧构造函数（无通知配置）
     */
    public PushReviewedEvent(Object source, String projectName, String author,
                             String branch, int score, String reviewResult) {
        this(source, projectName, null, author, branch, score, reviewResult, null);
    }

    /**
     * 完整构造函数
     */
    public PushReviewedEvent(Object source, String projectName, String platform, String author,
                             String branch, int score, String reviewResult, String notificationChannels) {
        super(source);
        this.projectName = projectName;
        this.platform = platform;
        this.author = author;
        this.branch = branch;
        this.score = score;
        this.reviewResult = reviewResult;
        this.notificationChannels = notificationChannels;
    }
}
