package com.campus.lostfound.service;

import com.campus.lostfound.entity.Post;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MatchService 单元测试：computeScore 四维加权打分与图像融合逻辑（纯计算，不依赖数据库）
 * <p>
 * imageWeight / textWeight 为 @Value 注入的配置项，直接 new 出的对象不会注入，
 * 这里通过反射设置为默认值 0.6 / 0.4，以验证完整的融合公式。
 */
class MatchServiceTest {

    private final MatchService matchService = new MatchService();

    @BeforeEach
    void injectWeights() throws Exception {
        setField("imageWeight", 0.6);
        setField("textWeight", 0.4);
        setField("imageMinConfidence", 0.5);
    }

    private void setField(String name, double value) throws Exception {
        Field f = MatchService.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(matchService, value);
    }

    private Post post(String title, String category, String location, LocalDateTime time) {
        Post p = new Post();
        p.setTitle(title);
        p.setCategory(category);
        p.setLocation(location);
        p.setLostTime(time);
        return p;
    }

    @Test
    void identicalPostsScoreNearOne() {
        LocalDateTime t = LocalDateTime.of(2026, 8, 27, 10, 0);
        Post a = post("蓝色书包", "书包/背包", "图书馆三楼", t);
        Post b = post("蓝色书包", "书包/背包", "图书馆三楼", t);

        MatchService.ScoreResult r = matchService.computeScore(a, b);

        assertEquals(1.0, r.nameScore, 1e-9);
        assertEquals(1.0, r.categoryScore, 1e-9);
        assertEquals(1.0, r.locationScore, 1e-9);
        assertEquals(1.0, r.timeScore, 1e-9);
        assertEquals(1.0, r.textScore, 1e-9);
        assertNull(r.imageScore, "无特征向量时图像分应为 null");
        assertEquals(r.textScore, r.finalScore, 1e-9);
    }

    @Test
    void synonymCategoryBoostsScore() {
        // "蓝色书包" vs "蓝色双肩包"：名称包含、类别同义词归一化后相同、地点包含、时间相近
        LocalDateTime t = LocalDateTime.of(2026, 8, 27, 10, 0);
        Post lost = post("蓝色书包", "书包/背包", "图书馆三楼", t);
        Post found = post("蓝色双肩包", "双肩包", "图书馆", t);

        MatchService.ScoreResult r = matchService.computeScore(lost, found);

        assertEquals(1.0, r.categoryScore, 1e-9, "同义词归一化后类别应完全匹配");
        assertTrue(r.nameScore > 0.5, "包含关系应带来较高名称相似度");
        assertTrue(r.finalScore > 0.7, "整体匹配度应较高，实际: " + r.finalScore);
    }

    @Test
    void nullTimeYieldsNeutralTimeScore() {
        Post a = post("雨伞", "雨伞", "行政楼", null);
        Post b = post("雨伞", "雨伞", "行政楼", null);

        MatchService.ScoreResult r = matchService.computeScore(a, b);

        assertEquals(0.5, r.timeScore, 1e-9);
    }

    @Test
    void imageScoreFusesWithTextScore() {
        LocalDateTime t = LocalDateTime.of(2026, 8, 27, 10, 0);
        Post a = post("黑色钱包", "钱包/包", "第一教学楼", t);
        Post b = post("黑色钱包", "钱包/包", "第一教学楼", t);
        // 相同特征向量 → 余弦相似度 1.0
        a.setFeatureVector("1.0,0.0");
        b.setFeatureVector("1.0,0.0");

        MatchService.ScoreResult r = matchService.computeScore(a, b);

        assertNotNull(r.imageScore);
        assertEquals(1.0, r.imageScore, 1e-9);
        // 综合分 = 0.6 * 图像 + 0.4 * 文字
        double expected = 0.6 * r.imageScore + 0.4 * r.textScore;
        assertEquals(expected, r.finalScore, 1e-9);
    }

    @Test
    void orthogonalVectorsGiveZeroImageScore() {
        LocalDateTime t = LocalDateTime.of(2026, 8, 27, 10, 0);
        Post a = post("黑色钱包", "钱包/包", "第一教学楼", t);
        Post b = post("黑色钱包", "钱包/包", "第一教学楼", t);
        a.setFeatureVector("1.0,0.0");
        b.setFeatureVector("0.0,1.0");

        MatchService.ScoreResult r = matchService.computeScore(a, b);

        assertNotNull(r.imageScore);
        assertEquals(0.0, r.imageScore, 1e-9);
        // 图像相似度过低（< imageMinConfidence），图像不参与融合，退化为纯文字匹配
        assertEquals(r.textScore, r.finalScore, 1e-9);
    }
}
