package org.ruoyi.codereview.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 配置服务测试
 */
@Tag("unit")
class ReviewConfigServiceTest {

    private ReviewConfigService configService;

    @BeforeEach
    void setUp() {
        // 使用反射创建实例（不依赖 Spring 容器和数据库）
        try {
            configService = new ReviewConfigService(null, null, null);
            // 手动设置缓存值进行测试
            setCacheValue("review.enabled", "true");
            setCacheValue("review.max_tokens", "10000");
            setCacheValue("scoring.pass_score", "60");
            setCacheValue("scoring.excellent_score", "90");
            setCacheValue("scoring.functionality.weight", "40");
            setCacheValue("scoring.security.weight", "30");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setCacheValue(String key, String value) {
        try {
            var field = ReviewConfigService.class.getDeclaredField("configCache");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, String> cache = (Map<String, String>) field.get(configService);
            cache.put(key, value);

            // 标记为已初始化
            var initField = ReviewConfigService.class.getDeclaredField("initialized");
            initField.setAccessible(true);
            initField.set(configService, true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("获取字符串配置")
    void testGetString() {
        assertEquals("true", configService.getString("review.enabled"));
        assertEquals("10000", configService.getString("review.max_tokens"));
        assertNull(configService.getString("nonexistent"));
    }

    @Test
    @DisplayName("获取字符串配置（带默认值）")
    void testGetStringWithDefault() {
        assertEquals("true", configService.getString("review.enabled", "false"));
        assertEquals("default", configService.getString("nonexistent", "default"));
    }

    @Test
    @DisplayName("获取整数配置")
    void testGetInt() {
        assertEquals(10000, configService.getInt("review.max_tokens", 0));
        assertEquals(60, configService.getInt("scoring.pass_score", 0));
        assertEquals(999, configService.getInt("nonexistent", 999));
    }

    @Test
    @DisplayName("获取布尔配置")
    void testGetBoolean() {
        assertTrue(configService.getBoolean("review.enabled", false));
        assertFalse(configService.getBoolean("nonexistent", false));
    }

    @Test
    @DisplayName("获取评分等级")
    void testGetScoreLevel() {
        assertEquals("优秀", configService.getScoreLevel(95));
        assertEquals("良好", configService.getScoreLevel(85));
        assertEquals("合格", configService.getScoreLevel(70));
        assertEquals("待改进", configService.getScoreLevel(60));
        assertEquals("不通过", configService.getScoreLevel(50));
    }

    @Test
    @DisplayName("是否通过审查")
    void testIsPassed() {
        assertTrue(configService.isPassed(60));
        assertTrue(configService.isPassed(80));
        assertFalse(configService.isPassed(59));
        assertFalse(configService.isPassed(0));
    }

    @Test
    @DisplayName("获取维度权重")
    void testGetDimensionWeight() {
        assertEquals(40, configService.getDimensionWeight("functionality"));
        assertEquals(30, configService.getDimensionWeight("security"));
        assertEquals(0, configService.getDimensionWeight("nonexistent"));
    }

    @Test
    @DisplayName("计算加权总分")
    void testCalculateWeightedScore() {
        Map<String, BigDecimal> scores = Map.of(
            "functionality", new BigDecimal("80"),
            "security", new BigDecimal("90")
        );

        BigDecimal weightedScore = configService.calculateWeightedScore(scores);
        // 80 * 40 / 100 + 90 * 30 / 100 = 32 + 27 = 59, totalWeight = 70
        // 59 / 70 * 100 = 84
        assertTrue(weightedScore.compareTo(BigDecimal.valueOf(80)) >= 0);
        assertTrue(weightedScore.compareTo(BigDecimal.valueOf(90)) <= 0);
    }
}
