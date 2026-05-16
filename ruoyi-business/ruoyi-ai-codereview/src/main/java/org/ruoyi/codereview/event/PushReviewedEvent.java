package org.ruoyi.codereview.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Push 审查完成事件
 */
@Getter
public class PushReviewedEvent extends ApplicationEvent {

    private final String projectName;
    private final String author;
    private final String branch;
    private final int score;
    private final String reviewResult;

    public PushReviewedEvent(Object source, String projectName, String author,
                             String branch, int score, String reviewResult) {
        super(source);
        this.projectName = projectName;
        this.author = author;
        this.branch = branch;
        this.score = score;
        this.reviewResult = reviewResult;
    }
}