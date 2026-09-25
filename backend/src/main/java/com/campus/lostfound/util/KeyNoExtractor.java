package com.campus.lostfound.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 关键号码提取：从文本（描述 / OCR 结果）中提取学号、身份证号等唯一标识，
 * 用于证件卡类物品的精确匹配。
 */
public class KeyNoExtractor {

    private KeyNoExtractor() {
    }

    /** 身份证号：18 位（末位可能为 X） */
    private static final Pattern ID_CARD = Pattern.compile("(?<!\\d)(\\d{17}[0-9Xx])(?!\\d)");
    /** 学号 / 卡号：8~12 位数字 */
    private static final Pattern STUDENT_NO = Pattern.compile("(?<!\\d)(\\d{8,12})(?!\\d)");

    /**
     * 从多个文本片段中提取关键号码。优先身份证号（18 位），其次学号/卡号（8~12 位，排除手机号）。
     * 未命中返回 null。
     */
    public static String extract(String... texts) {
        StringBuilder raw = new StringBuilder();
        for (String t : texts) {
            if (t != null) {
                raw.append(t).append(' ');
            }
        }
        String text = raw.toString();

        Matcher id = ID_CARD.matcher(text);
        if (id.find()) {
            return id.group(1).toUpperCase();
        }
        Matcher stu = STUDENT_NO.matcher(text);
        while (stu.find()) {
            String n = stu.group(1);
            // 排除手机号（1 开头的 11 位）
            if (n.length() == 11 && n.startsWith("1")) {
                continue;
            }
            return n;
        }
        return null;
    }
}
