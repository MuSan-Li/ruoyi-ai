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
 * <p>
 * 避免频繁查询数据库获取模型配置，支持懒加载
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
     * 获取模型配置（按名称）- 支持懒加载
     */
    public ChatModelVo getByName(String modelName) {
        if (modelName == null || modelName.isEmpty()) {
            return null;
        }

        ChatModelVo cached = modelByNameCache.get(modelName);
        if (cached != null) {
            return cached;
        }

        // 懒加载：缓存未命中时从数据库加载
        return loadByName(modelName);
    }

    /**
     * 获取模型配置（按ID）- 支持懒加载
     */
    public ChatModelVo getById(Long modelId) {
        if (modelId == null) {
            return null;
        }

        ChatModelVo cached = modelByIdCache.get(modelId);
        if (cached != null) {
            return cached;
        }

        // 懒加载：缓存未命中时从数据库加载
        return loadById(modelId);
    }

    /**
     * 懒加载：按名称从数据库加载并缓存
     */
    private synchronized ChatModelVo loadByName(String modelName) {
        // 双重检查，防止并发重复加载
        ChatModelVo cached = modelByNameCache.get(modelName);
        if (cached != null) {
            return cached;
        }

        try {
            log.debug("懒加载模型配置: name={}", modelName);
            ChatModelVo model = chatModelService.selectModelByName(modelName);
            if (model != null) {
                put(model);
            }
            return model;
        } catch (Exception e) {
            log.error("加载模型配置失败: name={}", modelName, e);
            return null;
        }
    }

    /**
     * 懒加载：按ID从数据库加载并缓存
     */
    private synchronized ChatModelVo loadById(Long modelId) {
        // 双重检查，防止并发重复加载
        ChatModelVo cached = modelByIdCache.get(modelId);
        if (cached != null) {
            return cached;
        }

        try {
            log.debug("懒加载模型配置: id={}", modelId);
            ChatModelVo model = chatModelService.queryById(modelId);
            if (model != null) {
                put(model);
            }
            return model;
        } catch (Exception e) {
            log.error("加载模型配置失败: id={}", modelId, e);
            return null;
        }
    }

    /**
     * 刷新缓存（清空缓存，下次访问时懒加载）
     */
    public synchronized void refreshCache() {
        log.info("刷新模型配置缓存...");
        modelByNameCache.clear();
        modelByIdCache.clear();
        initialized = true;
        log.info("模型配置缓存已清空，将使用懒加载模式");
    }

    /**
     * 预热缓存：加载所有模型
     */
    public synchronized void warmUp() {
        log.info("预热模型配置缓存...");
        try {
            modelByNameCache.clear();
            modelByIdCache.clear();

            // 加载所有模型（如果 chatModelService 有相应方法）
            // 目前保持懒加载模式，不主动加载

            initialized = true;
            log.info("模型配置缓存预热完成");
        } catch (Exception e) {
            log.error("预热模型配置缓存失败", e);
        }
    }

    /**
     * 添加模型到缓存
     */
    public void put(ChatModelVo model) {
        if (model == null) return;
        if (model.getModelName() != null) {
            modelByNameCache.put(model.getModelName(), model);
            log.debug("模型缓存添加: name={}", model.getModelName());
        }
        if (model.getId() != null) {
            modelByIdCache.put(model.getId(), model);
            log.debug("模型缓存添加: id={}", model.getId());
        }
    }

    /**
     * 从缓存移除模型
     */
    public void remove(Long modelId, String modelName) {
        if (modelId != null) {
            modelByIdCache.remove(modelId);
            log.debug("模型缓存移除: id={}", modelId);
        }
        if (modelName != null) {
            modelByNameCache.remove(modelName);
            log.debug("模型缓存移除: name={}", modelName);
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

    /**
     * 检查缓存是否包含指定模型
     */
    public boolean containsByName(String modelName) {
        return modelByNameCache.containsKey(modelName);
    }

    public boolean containsById(Long modelId) {
        return modelByIdCache.containsKey(modelId);
    }
}
