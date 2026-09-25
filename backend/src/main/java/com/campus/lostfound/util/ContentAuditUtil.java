package com.campus.lostfound.util;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 文本内容初审核工具（轻量规则库，纯字符串匹配，无第三方依赖）。
 * <p>
 * 判定结果：
 * <ul>
 *   <li>HARD —— 命中硬性违禁词，AI 初审核直接拒绝</li>
 *   <li>SOFT —— 命中广告/联系方式等软敏感特征，打预警供人工复核</li>
 *   <li>NONE —— 未命中任何规则</li>
 * </ul>
 * 用于「AI 初审核 + 人工二审」两级审核的文本维度初筛。
 */
public class ContentAuditUtil {

    /** 硬违禁词：命中即拒绝（违法违规 / 诈骗 / 赌博 / 代考等） */
    private static final List<String> HARD_WORDS = Arrays.asList(
            "代考", "替考", "代写", "代刷", "刷单", "博彩", "赌博", "彩票代购",
            "贷款", "套现", "办证", "出售违禁", "色情", "招嫖", "裸聊", "传销");

    /** 软敏感词：命中打预警（广告 / 商业推广倾向） */
    private static final List<String> SOFT_WORDS = Arrays.asList(
            "低价", "打折", "促销", "兼职", "微商", "代购", "招商", "加盟", "推广");

    /** 联系方式特征：失物招领应通过站内私聊，公开联系方式属隐私风险 */
    private static final Pattern PHONE = Pattern.compile("1[3-9]\\d{9}");
    private static final Pattern QQ = Pattern.compile("[Qq]{2}\\s*[:：]?\\s*\\d{5,}");
    private static final Pattern WECHAT = Pattern.compile("[Vv]?[Xx微信]\\s*[:：]?\\s*[A-Za-z0-9_-]{5,}");

    private ContentAuditUtil() {
    }

    /**
     * 对文本做敏感词初筛，返回 HARD / SOFT / NONE。
     */
    public static String audit(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "NONE";
        }
        for (String w : HARD_WORDS) {
            if (text.contains(w)) {
                return "HARD";
            }
        }
        for (String w : SOFT_WORDS) {
            if (text.contains(w)) {
                return "SOFT";
            }
        }
        if (PHONE.matcher(text).find() || QQ.matcher(text).find() || WECHAT.matcher(text).find()) {
            return "SOFT";
        }
        return "NONE";
    }
}
