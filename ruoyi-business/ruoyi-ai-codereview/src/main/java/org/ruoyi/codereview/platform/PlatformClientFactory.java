package org.ruoyi.codereview.platform;

import lombok.extern.slf4j.Slf4j;
import org.ruoyi.codereview.entity.dto.PlatformConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Git 平台客户端工厂
 * <p>
 * 使用策略模式自动注册各平台客户端
 * <p>
 * 扩展新平台只需：
 * 1. 实现 PlatformClientProvider 接口
 * 2. 添加 @Component 注解
 * 工厂会自动发现并注册
 */
@Slf4j
@Component
public class PlatformClientFactory {

    private final Map<String, PlatformClientProvider> providers;
    private final RestTemplate restTemplate;

    /**
     * 构造函数：自动注入所有 Provider 并注册
     */
    @Autowired
    public PlatformClientFactory(List<PlatformClientProvider> providerList,
                                  @Autowired(required = false) RestTemplate codereviewRestTemplate) {
        this.providers = providerList.stream()
                .collect(Collectors.toMap(
                        PlatformClientProvider::getPlatformType,
                        Function.identity()
                ));
        this.restTemplate = codereviewRestTemplate;

        log.info("平台客户端工厂初始化完成，注册平台: {}", providers.keySet());
    }

    /**
     * 创建平台客户端
     *
     * @param config 平台配置
     * @return 平台客户端实例
     */
    public GitPlatformClient createClient(PlatformConfig config) {
        if (config == null || !config.isEnabled()) {
            throw new IllegalArgumentException("平台配置无效或未启用");
        }

        String platform = config.getPlatform().toLowerCase();
        log.info("创建平台客户端: platform={}, url={}", platform, config.getUrl());

        PlatformClientProvider provider = providers.get(platform);
        if (provider == null) {
            throw new IllegalArgumentException("不支持的平台: " + platform);
        }

        return provider.createClient(config, restTemplate);
    }

    /**
     * 创建平台客户端（使用指定 RestTemplate）
     *
     * @param config      平台配置
     * @param restTemplate RestTemplate
     * @return 平台客户端实例
     */
    public GitPlatformClient createClient(PlatformConfig config, RestTemplate restTemplate) {
        if (config == null || !config.isEnabled()) {
            throw new IllegalArgumentException("平台配置无效或未启用");
        }

        String platform = config.getPlatform().toLowerCase();
        PlatformClientProvider provider = providers.get(platform);
        if (provider == null) {
            throw new IllegalArgumentException("不支持的平台: " + platform);
        }

        return provider.createClient(config, restTemplate);
    }

    /**
     * 获取已注册的平台类型
     */
    public java.util.Set<String> getRegisteredPlatforms() {
        return providers.keySet();
    }

    /**
     * 判断平台是否支持
     */
    public boolean isPlatformSupported(String platform) {
        return providers.containsKey(platform.toLowerCase());
    }
}
