package org.ruoyi.codereview.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Merge Request 审查完成事件
 */
@Getter
public class MergeRequestReviewedEvent extends ApplicationEvent {

    private final String projectName;
    private final String author;
    private final String sourceBranch;
    private final String targetBranch;
    private final String url;
    private final int score;
    private final String reviewResult;

    public MergeRequestReviewedEvent(Object source, String projectName, String author,
                                      String sourceBranch, String targetBranch,
                                      String url, int score, String reviewResult) {
        super(source);
        this.projectName = projectName;
        this.author = author;
        this.sourceBranch = sourceBranch;
        this.targetBranch = targetBranch;
        this.url = url;
        this.score = score;
        this.reviewResult = reviewResult;
    }
}