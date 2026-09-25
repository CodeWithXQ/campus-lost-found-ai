package com.campus.lostfound.util;

/**
 * 字符串相似度工具（编辑距离 Levenshtein + 包含关系加权）
 * 用于匹配算法的"物品名称"与"地点"维度打分
 */
public class StringSimilarity {

    private StringSimilarity() {
    }

    /**
     * 编辑距离
     */
    public static int levenshtein(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        int n = a.length();
        int m = b.length();
        if (n == 0) return m;
        if (m == 0) return n;
        int[] prev = new int[m + 1];
        int[] curr = new int[m + 1];
        for (int j = 0; j <= m; j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= n; i++) {
            curr[0] = i;
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[m];
    }

    /**
     * 归一化编辑距离相似度：0~1
     * 1 - dist / max(lenA, lenB)
     */
    public static double editSimilarity(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        int maxLen = Math.max(a.length(), b.length());
        if (maxLen == 0) return 1.0;
        int dist = levenshtein(a, b);
        return 1.0 - (double) dist / maxLen;
    }

    /**
     * 综合相似度：编辑距离 70% + 包含关系 30%（包含关系给高分）
     * 处理"蓝色书包"与"蓝色双肩包"这类场景
     */
    public static double similarity(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        a = a.trim();
        b = b.trim();
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        if (a.equals(b)) return 1.0;
        double edit = editSimilarity(a, b);
        double contain = 0.0;
        if (a.contains(b) || b.contains(a)) {
            // 短串全部包含在长串中
            int shortLen = Math.min(a.length(), b.length());
            contain = 0.85 + 0.15 * (double) shortLen / Math.max(a.length(), b.length());
        }
        return Math.max(edit, contain);
    }
}
