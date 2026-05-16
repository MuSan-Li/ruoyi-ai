package org.ruoyi.codereview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 审查规则实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cr_review_rule")
public class ReviewRule extends TenantEntityLocalDateTime {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 规则名称 */
    private String ruleName;

    /** 规则编码 */
    private String ruleCode;

    /** 审查维度 */
    private String dimension;

    /** 权重(满分) */
    private Integer weight;

    /** 规则说明 */
    private String description;

    /** 检查正则表达式(JSON数组) */
    private String checkPattern;

    /** 严重级别 */
    private String severity;

    /** 适用语言 */
    private String languagePattern;

    /** 适用文件路径模式 */
    private String filePattern;

    /** 是否启用 */
    private Integer enabled;

    /** 排序 */
    private Integer sortOrder;
}
