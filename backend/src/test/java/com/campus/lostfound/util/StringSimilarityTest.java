package com.campus.lostfound.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * StringSimilarity 单元测试：编辑距离、编辑相似度、综合相似度（含包含关系加权）
 */
class StringSimilarityTest {

    @Test
    void levenshteinBasic() {
        assertEquals(0, StringSimilarity.levenshtein("abc", "abc"));
        assertEquals(1, StringSimilarity.levenshtein("abc", "abd"));
        assertEquals(3, StringSimilarity.levenshtein("kitten", "sitting"));
        assertEquals(4, StringSimilarity.levenshtein("", "test"));
    }

    @Test
    void editSimilarityIdenticalIsOne() {
        assertEquals(1.0, StringSimilarity.editSimilarity("蓝色书包", "蓝色书包"), 1e-9);
        assertEquals(1.0, StringSimilarity.editSimilarity("", ""), 1e-9);
    }

    @Test
    void similarityHandlesContainment() {
        // "蓝色书包" 被 "蓝色双肩包" 包含，包含关系应给高分（> 0.5）
        double s = StringSimilarity.similarity("蓝色书包", "蓝色双肩包");
        assertTrue(s > 0.5, "包含关系应返回较高相似度，实际: " + s);
    }

    @Test
    void similarityIdenticalIsOne() {
        assertEquals(1.0, StringSimilarity.similarity("钱包", "钱包"), 1e-9);
    }

    @Test
    void similarityEmptyReturnsZero() {
        assertEquals(0.0, StringSimilarity.similarity("", ""), 1e-9);
        assertEquals(0.0, StringSimilarity.similarity(null, "abc"), 1e-9);
        assertEquals(0.0, StringSimilarity.similarity("abc", null), 1e-9);
    }

    @Test
    void similarityUnrelatedStringsAreLow() {
        double s = StringSimilarity.similarity("雨伞", "笔记本电脑");
        assertTrue(s < 0.3, "完全无关的字符串相似度应较低，实际: " + s);
    }
}
