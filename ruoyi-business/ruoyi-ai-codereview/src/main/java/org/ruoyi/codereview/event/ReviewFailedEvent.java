package org.ruoyi.codereview.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 审查失败事件
 */
@Getter
public class ReviewFailedEvent extends ApplicationEvent {

    private final String projectName;
    private final String platform;
    private final String eventType;
    private final String errorMessage;
    private final String payload;

    public ReviewFailedEvent(Object source, String projectName, String platform,
                             String eventType, String errorMessage, String payload) {
        super(source);
        this.projectName = projectName;
        this.platform = platform;
        this.eventType = eventType;
        this.errorMessage = errorMessage;
        this.payload = payload;
    }
}
