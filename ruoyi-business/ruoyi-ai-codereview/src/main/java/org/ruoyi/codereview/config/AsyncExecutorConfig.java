package org.ruoyi.codereview.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务线程池配置
 * <p>
 * 为代码审查模块配置专用线程池，避免使用 Spring 默认线程池
 * <p>
 * 配置说明：
 * - 核心线程数：4（日常负载）
 * - 最大线程数：16（峰值负载）
 * - 队列容量：100（缓冲任务）
 * - 拒绝策略：CallerRunsPolicy（降级到调用者线程）
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncExecutorConfig {

    /** 核心线程数 */
    private static final int CORE_POOL_SIZE = 4;
    /** 最大线程数 */
    private static final int MAX_POOL_SIZE = 16;
    /** 队列容量 */
    private static final int QUEUE_CAPACITY = 100;
    /** 线程空闲时间（秒） */
    private static final int KEEP_ALIVE_SECONDS = 60;
    /** 线程名前缀 */
    private static final String THREAD_NAME_PREFIX = "code-review-";

    /**
     * 代码审查专用线程池
     * <p>
     * 用于 @Async("codeReviewExecutor") 标注的异步方法
     */
    @Bean("codeReviewExecutor")
    public Executor codeReviewExecutor() {
        log.info("初始化代码审查线程池: core={}, max={}, queue={}",
                CORE_POOL_SIZE, MAX_POOL_SIZE, QUEUE_CAPACITY);

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAX_POOL_SIZE);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setKeepAliveSeconds(KEEP_ALIVE_SECONDS);
        executor.setThreadNamePrefix(THREAD_NAME_PREFIX);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();

        return executor;
    }
}
