package com.campus.lostfound.util;

/**
 * 文本规范化工具：消除全半角、大小写、空白等无关差异，为类别 / 描述等文本的归一化做预处理。
 */
public class TextNormalizer {

    private TextNormalizer() {
    }

    /**
     * 规范化：全角转半角 → 英文小写 → 去所有空白字符。
     * 示例："iPhone 13" → "iphone13"、"ＡＢＣ" → "abc"。
     */
    public static String normalize(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '　') {
                // 全角空格 → 半角空格
                c = ' ';
            } else if (c >= '！' && c <= '～') {
                // 全角 ASCII（！～）→ 半角（!~）
                c = (char) (c - 0xFEE0);
            }
            sb.append(c);
        }
        return sb.toString().toLowerCase().replaceAll("\\s+", "");
    }
}
