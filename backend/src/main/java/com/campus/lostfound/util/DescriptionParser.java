package com.campus.lostfound.util;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 物品描述属性抽取与相似度计算。
 * <p>
 * 从标题 + 描述中抽取颜色、品牌、型号等强匹配证据，并计算两条描述之间的属性相似度。
 * 核心原则：属性"缺失 ≠ 不匹配"（给中性分），属性"明确冲突 = 负证据"（降分）。
 */
public class DescriptionParser {

    private DescriptionParser() {
    }

    /** 基本颜色词（"深蓝"会通过 contains 命中"蓝"，故不重复收录复合色） */
    private static final String[] COLORS = {
            "黑", "白", "红", "蓝", "绿", "黄", "粉", "紫", "灰", "银", "金", "棕", "橙", "青",
            "透明", "咖啡", "藏青", "藏蓝", "米白", "玫红", "军绿", "深蓝", "浅蓝", "深灰", "浅灰", "天蓝", "卡其"
    };

    private static final String[] BRANDS = {
            "华为", "苹果", "小米", "oppo", "vivo", "三星", "联想", "戴尔", "惠普", "华硕", "索尼",
            "耐克", "阿迪达斯", "阿迪", "李宁", "安踏", "新百伦", "荣耀", "红米", "魅族", "一加", "realme", "iqoo", "佳能", "尼康"
    };

    /** 型号（英文 + 数字组合，如 mate60 / iphone13 / p60） */
    private static final Pattern MODEL_PATTERN = Pattern.compile("([a-z]+\\d+[a-z0-9]*)");

    public static class Attrs {
        public final Set<String> colors = new HashSet<>();
        public final Set<String> brands = new HashSet<>();
        public final Set<String> models = new HashSet<>();
    }

    /** 从多个文本片段中抽取属性（title + description） */
    public static Attrs extract(String... texts) {
        Attrs a = new Attrs();
        StringBuilder raw = new StringBuilder();
        for (String t : texts) {
            if (t != null) {
                raw.append(t).append(' ');
            }
        }
        String text = TextNormalizer.normalize(raw.toString());

        // 1. 品牌
        for (String b : BRANDS) {
            if (text.contains(TextNormalizer.normalize(b))) {
                a.brands.add(b);
            }
        }
        // 2. 去掉品牌词后再匹配颜色，避免"红米"中的"红"被误判为颜色
        String noBrand = text;
        for (String b : a.brands) {
            noBrand = noBrand.replace(TextNormalizer.normalize(b), " ");
        }
        for (String c : COLORS) {
            if (noBrand.contains(c)) {
                a.colors.add(c);
            }
        }
        // 3. 型号
        Matcher m = MODEL_PATTERN.matcher(noBrand);
        while (m.find()) {
            String tok = m.group(1);
            if (tok.length() >= 2 && tok.length() <= 12) {
                a.models.add(tok);
            }
        }
        return a;
    }

    /**
     * 两条描述（标题 + 描述）的属性相似度（0~1）。
     * 双方都无任何属性时返回中性分 0.5。
     */
    public static double similarity(String titleA, String descA, String titleB, String descB) {
        Attrs a = extract(titleA, descA);
        Attrs b = extract(titleB, descB);

        double colorScore = attrScore(a.colors, b.colors);
        double brandScore = attrScore(a.brands, b.brands);
        double modelScore = attrScore(a.models, b.models);

        boolean aEmpty = a.colors.isEmpty() && a.brands.isEmpty() && a.models.isEmpty();
        boolean bEmpty = b.colors.isEmpty() && b.brands.isEmpty() && b.models.isEmpty();
        if (aEmpty && bEmpty) {
            return 0.5;
        }

        double score = colorScore * 0.4 + brandScore * 0.3 + modelScore * 0.3;
        // 颜色冲突惩罚：双方都明确写了颜色但无交集，是强负证据
        if (!a.colors.isEmpty() && !b.colors.isEmpty() && colorScore <= 0.0) {
            score -= 0.25;
        }
        return Math.max(0.0, Math.min(1.0, score));
    }

    /** 单属性相似度：缺失给中性 0.5，有交集给 1.0，明确冲突给 0.0 */
    private static double attrScore(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0.5;
        }
        Set<String> inter = new HashSet<>(a);
        inter.retainAll(b);
        return inter.isEmpty() ? 0.0 : 1.0;
    }
}
