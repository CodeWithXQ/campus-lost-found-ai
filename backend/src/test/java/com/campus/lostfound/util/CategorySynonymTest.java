package com.campus.lostfound.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * CategorySynonym 单元测试：类别别名归一化
 */
class CategorySynonymTest {

    @Test
    void normalizeCanonicalStaysSame() {
        assertEquals("书包/背包", CategorySynonym.normalize("书包/背包"));
        assertEquals("手机", CategorySynonym.normalize("手机"));
    }

    @Test
    void normalizeAliasToCanonical() {
        assertEquals("书包/背包", CategorySynonym.normalize("双肩包"));
        assertEquals("书包/背包", CategorySynonym.normalize("背包"));
        assertEquals("耳机", CategorySynonym.normalize("蓝牙耳机"));
        assertEquals("手机", CategorySynonym.normalize("iphone"));
        assertEquals("手机", CategorySynonym.normalize("iPhone"));
        assertEquals("证件/卡", CategorySynonym.normalize("校园卡"));
        assertEquals("钱包/包", CategorySynonym.normalize("钱夹"));
    }

    @Test
    void normalizeUnknownReturnsOriginal() {
        assertEquals("航天器", CategorySynonym.normalize("航天器"));
    }

    @Test
    void normalizeNullReturnsEmpty() {
        assertEquals("", CategorySynonym.normalize(null));
    }

    @Test
    void normalizeTrimsAndHandlesEmpty() {
        assertEquals("手机", CategorySynonym.normalize(" 手机 "));
        assertEquals("", CategorySynonym.normalize("   "));
    }
}
