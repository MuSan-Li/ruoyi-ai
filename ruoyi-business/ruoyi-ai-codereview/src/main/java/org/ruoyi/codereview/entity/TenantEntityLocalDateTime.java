package org.ruoyi.codereview.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.ruoyi.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/**
 * 租户实体基类
 * 继承框架 BaseEntity 以获得 MetaObjectHandler 自动填充审计字段
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TenantEntityLocalDateTime extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 租户编号
     */
    private String tenantId;

}
