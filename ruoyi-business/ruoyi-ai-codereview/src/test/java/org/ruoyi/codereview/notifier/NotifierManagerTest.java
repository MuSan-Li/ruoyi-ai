package org.ruoyi.codereview.notifier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * NotifierManager 单元测试
 */
@Tag("dev")
class NotifierManagerTest {

    private NotifierManager notifierManager;
    private List<Notifier> notifiers;

    @BeforeEach
    void setUp() {
        notifiers = new ArrayList<>();
    }

    @Test
    @DisplayName("空通知器列表 - 不发送任何通知")
    void emptyNotifierList_noNotificationsSent() {
        notifierManager = new NotifierManager(notifiers);

        // 不应该抛出异常
        assertDoesNotThrow(() -> {
            notifierManager.notifyMergeRequestReviewed(
                "test-project", "testuser", "feature", "main",
                "https://test.url", 85, "Good code"
            );
        });
    }

    @Test
    @DisplayName("MR 审查通知 - 发送到所有通知器")
    void mrNotification_sentToAllNotifiers() {
        Notifier mockNotifier1 = mock(Notifier.class);
        Notifier mockNotifier2 = mock(Notifier.class);
        when(mockNotifier1.send(any(), any())).thenReturn(true);
        when(mockNotifier2.send(any(), any())).thenReturn(true);

        notifiers.add(mockNotifier1);
        notifiers.add(mockNotifier2);

        notifierManager = new NotifierManager(notifiers);

        notifierManager.notifyMergeRequestReviewed(
            "test-project", "testuser", "feature", "main",
            "https://test.url", 85, "Good code"
        );

        verify(mockNotifier1).send(any(), any());
        verify(mockNotifier2).send(any(), any());
    }

    @Test
    @DisplayName("Push 审查通知 - 发送到所有通知器")
    void pushNotification_sentToAllNotifiers() {
        Notifier mockNotifier = mock(Notifier.class);
        when(mockNotifier.send(any(), any())).thenReturn(true);

        notifiers.add(mockNotifier);
        notifierManager = new NotifierManager(notifiers);

        notifierManager.notifyPushReviewed("test-project", "testuser", "main", 90, "Excellent");

        verify(mockNotifier).send(contains("代码审查完成"), any());
    }

    @Test
    @DisplayName("日报通知 - 正确发送")
    void dailyReportNotification_sentCorrectly() {
        Notifier mockNotifier = mock(Notifier.class);
        when(mockNotifier.send(any(), any())).thenReturn(true);

        notifiers.add(mockNotifier);
        notifierManager = new NotifierManager(notifiers);

        String report = "今日审查统计: 10个MR, 平均分85";
        notifierManager.notifyDailyReport(report);

        verify(mockNotifier).send(contains("日报"), contains("审查统计"));
    }

    @Test
    @DisplayName("通知器异常 - 继续发送其他通知器")
    void notifierException_continuesToOtherNotifiers() {
        Notifier failingNotifier = mock(Notifier.class);
        Notifier workingNotifier = mock(Notifier.class);

        when(failingNotifier.send(any(), any())).thenThrow(new RuntimeException("Connection failed"));
        when(workingNotifier.send(any(), any())).thenReturn(true);

        notifiers.add(failingNotifier);
        notifiers.add(workingNotifier);

        notifierManager = new NotifierManager(notifiers);

        // 不应该抛出异常
        assertDoesNotThrow(() -> {
            notifierManager.notifyMergeRequestReviewed(
                "test-project", "testuser", "feature", "main",
                "https://test.url", 85, "Good code"
            );
        });

        verify(workingNotifier).send(any(), any());
    }

    @Test
    @DisplayName("通知内容 - 包含所有必要信息")
    void notificationContent_containsAllInfo() {
        Notifier mockNotifier = mock(Notifier.class);
        when(mockNotifier.send(any(), any())).thenReturn(true);

        notifiers.add(mockNotifier);
        notifierManager = new NotifierManager(notifiers);

        notifierManager.notifyMergeRequestReviewed(
            "my-project", "john", "feature/test", "develop",
            "https://github.com/test/pr/1", 92, "Great work!"
        );

        verify(mockNotifier).send(
            contains("my-project"),
            argThat(content -> {
                String c = content.toString();
                return c.contains("my-project") &&
                       c.contains("john") &&
                       c.contains("feature/test") &&
                       c.contains("develop") &&
                       c.contains("92/100") &&
                       c.contains("github.com");
            })
        );
    }
}
