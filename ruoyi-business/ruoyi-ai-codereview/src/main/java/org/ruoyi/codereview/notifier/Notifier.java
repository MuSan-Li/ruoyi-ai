package org.ruoyi.codereview.notifier;

/**
 * 通知接口
 */
public interface Notifier {

    /**
     * 发送通知
     *
     * @param title   标题
     * @param content 内容
     * @return 是否成功
     */
    boolean send(String title, String content);
}