package org.ruoyi.codereview.audit;

import java.lang.annotation.*;

/**
 * 审计注解
 * <p>
 * 标注需要记录审计日志的方法
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Auditable {

    /**
     * 操作名称
     */
    String value();

    /**
     * 操作描述
     */
    String description() default "";

    /**
     * 是否记录参数
     */
    boolean recordParams() default true;

    /**
     * 是否记录结果
     */
    boolean recordResult() default false;
}
