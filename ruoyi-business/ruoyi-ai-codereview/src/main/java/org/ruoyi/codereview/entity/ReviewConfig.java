package org.ruoyi.codereview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 代码审查配置实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cr_review_config")
public class ReviewConfig extends TenantEntityLocalDateTime {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 配置键 */
    private String configKey;

    /** 配置值 */
    private String configValue;

    /** 配置类型: STRING, NUMBER, BOOLEAN, JSON */
    private String configType;

    /** 配置说明 */
    private String description;

    /** 配置分类 */
    private String category;

    /** 排序 */
    private Integer sortOrder;

    /** 状态: 0=禁用, 1=启用 */
    private Integer status;
}
