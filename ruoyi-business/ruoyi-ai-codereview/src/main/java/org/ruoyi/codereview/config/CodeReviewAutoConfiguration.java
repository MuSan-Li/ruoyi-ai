package org.ruoyi.codereview.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 代码审查自动配置类，由 Spring Boot 自动加载
 * <p>
 * 通过 spring.factories 自动配置，统一管理模块的所有功能开关和子配置
 */
@EnableAsync
@EnableRetry
@EnableScheduling
@Configuration
@EnableConfigurationProperties(CodeReviewProperties.class)
@Import({
    NotifierConfig.class,
    PlatformClientConfig.class
})
public class CodeReviewAutoConfiguration {
}
