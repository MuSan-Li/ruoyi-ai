package org.ruoyi.codereview.platform;

import org.ruoyi.codereview.config.CodeReviewProperties;
import org.ruoyi.codereview.entity.dto.PlatformConfig;
import org.springframework.web.client.RestTemplate;

/**
 * 平台客户端提供者接口
 * <p>
 * 用于策略模式自动注册各平台客户端
 * <p>
 * 实现此接口并添加 @Component 注解即可自动注册到工厂
 */
public interface PlatformClientProvider {

    /**
     * 获取平台类型
     *
     * @return 平台类型标识（如 "gitlab", "github", "gitea"）
     */
    String getPlatformType();

    /**
     * 从动态配置创建客户端
     *
     * @param config      平台配置
     * @param restTemplate RestTemplate
     * @return 平台客户端实例
     */
    GitPlatformClient createClient(PlatformConfig config, RestTemplate restTemplate);

    /**
     * 从 YAML 配置创建客户端
     *
     * @param properties  YAML 配置属性
     * @param restTemplate RestTemplate
     * @return 平台客户端实例
     */
    GitPlatformClient createClient(CodeReviewProperties properties, RestTemplate restTemplate);

    /**
     * 判断该平台是否启用
     *
     * @param properties YAML 配置属性
     * @return 是否启用
     */
    default boolean isEnabled(CodeReviewProperties properties) {
        return true;
    }
}
