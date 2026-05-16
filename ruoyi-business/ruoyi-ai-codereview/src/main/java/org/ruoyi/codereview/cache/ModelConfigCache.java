package org.ruoyi.codereview.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.common.chat.domain.vo.chat.ChatModelVo;
import org.ruoyi.common.chat.service.chat.IChatModelService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模型配置缓存
 * 避免频繁查询数据库获取模型配置
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModelConfigCache {

    private final IChatModelService chatModelService;

    /** 模型名称 -> 模型配置 */
    private final Map<String, ChatModelVo> modelByNameCache = new ConcurrentHashMap<>();

    /** 模型ID -> 模型配置 */
    private final Map<Long, ChatModelVo> modelByIdCache = new ConcurrentHashMap<>();

    /** 缓存是否已初始化 */
    private volatile boolean initialized = false;

    /**
     * 获取模型配置（按名称）
     */
    public ChatModelVo getByName(String modelName) {
        if (!initialized) {
            refreshCache();
        }
        return modelByNameCache.get(modelName);
    }

    /**
     * 获取模型配置（按ID）
     */
    public ChatModelVo getById(Long modelId) {
        if (!initialized) {
            refreshCache();
        }
        return modelByIdCache.get(modelId);
    }

    /**
     * 刷新缓存
     */
    public synchronized void refreshCache() {
        log.info("刷新模型配置缓存...");
        try {
            // 清空旧缓存
            modelByNameCache.clear();
            modelByIdCache.clear();

            // 这里可以添加查询所有模型的逻辑
            // 目前保持懒加载模式

            initialized = true;
            log.info("模型配置缓存刷新完成");
        } catch (Exception e) {
            log.error("刷新模型配置缓存失败", e);
        }
    }

    /**
     * 添加模型到缓存
     */
    public void put(ChatModelVo model) {
        if (model == null) return;
        if (model.getModelName() != null) {
            modelByNameCache.put(model.getModelName(), model);
        }
        if (model.getId() != null) {
            modelByIdCache.put(model.getId(), model);
        }
    }

    /**
     * 从缓存移除模型
     */
    public void remove(Long modelId, String modelName) {
        if (modelId != null) {
            modelByIdCache.remove(modelId);
        }
        if (modelName != null) {
            modelByNameCache.remove(modelName);
        }
    }

    /**
     * 定时刷新缓存（每5分钟）
     */
    @Scheduled(fixedRate = 300000)
    public void scheduledRefresh() {
        if (initialized) {
            refreshCache();
        }
    }

    /**
     * 获取缓存大小
     */
    public int size() {
        return modelByNameCache.size();
    }
}
