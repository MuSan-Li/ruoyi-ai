package org.ruoyi.codereview.entity;

import lombok.Data;

/**
 * 代码变更信息
 */
@Data
public class CodeChange {

    /**
     * 文件路径
     */
    private String filePath;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 变更类型 (add/modify/delete)
     */
    private String changeType;

    /**
     * 新增行数
     */
    private int additions;

    /**
     * 删除行数
     */
    private int deletions;

    /**
     * 代码内容（Diff格式）
     */
    private String diff;
}