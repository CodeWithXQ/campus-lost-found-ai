package com.campus.lostfound.util;

import java.util.HashMap;
import java.util.Map;

/**
 * 物品类别同义词归一化工具。
 * <p>
 * 将用户输入的类别别名（如"双肩包""背包"）归一化为标准类别（如"书包/背包"），
 * 使匹配算法能识别"蓝色书包" vs "蓝色双肩包"这类类别不同字面但语义相同的情况。
 * <p>
 * 归一化分两层：
 * 1. 文本规范化（{@link TextNormalizer}）：全半角、大小写、去空格，消除无关差异；
 * 2. 别名映射（本类静态 Map）：别名 → 标准类别。
 * <p>
 * 同时提供 {@link #categorySimilarity} 计算两个类别的语义距离（相等 / 包含 / 同类 / 无关），
 * 用于匹配打分，替代原来的三档硬编码。
 */
public class CategorySynonym {

    private CategorySynonym() {
    }

    /** 别名 → 标准类别（key 已做文本规范化） */
    private static final Map<String, String> ALIAS = new HashMap<>();

    /** 标准类别 → 大类分组（用于类别语义距离的"同类"判定） */
    private static final Map<String, String> GROUP = new HashMap<>();

    static {
        map("手机", "智能手机", "华为手机", "苹果手机", "iphone", "小米", "苹果", "三星",
                "oppo", "vivo", "华为", "红米", "荣耀", "mate60", "p60", "一加", "魅族");
        map("钱包/包", "钱包", "皮夹", "手包", "卡包", "零钱包", "钱夹", "斜挎小包", "钥匙包", "手拿包", "证件包");
        map("钥匙", "钥匙串", "钥匙链", "车钥匙", "门禁钥匙", "钥匙扣");
        map("证件/卡", "校园卡", "学生卡", "学生证", "身份证", "银行卡", "一卡通", "饭卡",
                "公交卡", "借书卡", "社保卡", "水卡", "门禁卡", "考勤卡", "借阅证", "图书证");
        map("书包/背包", "书包", "双肩包", "背包", "双肩背包", "斜挎包", "电脑包", "帆布包", "登山包", "胸包", "双肩");
        map("书籍", "书本", "教材", "课本", "图书", "杂志", "复习资料", "kindle", "电子书", "作业本", "笔记本", "练习册");
        map("衣物", "衣服", "外套", "围巾", "帽子", "手套", "羽绒服", "卫衣", "校服",
                "鞋", "球鞋", "运动鞋", "拖鞋", "腰带", "牛仔裤", "t恤", "衬衫", "裙子");
        map("水杯", "保温杯", "杯子", "水瓶", "玻璃杯", "马克杯", "水壶", "咖啡杯", "随行杯", "运动水壶");
        map("眼镜", "太阳镜", "墨镜", "近视眼镜", "眼镜盒", "隐形眼镜");
        map("耳机", "蓝牙耳机", "耳塞", "耳麦", "无线耳机", "头戴耳机", "airpods", "骨传导耳机");
        map("电脑/电子设备", "笔记本电脑", "平板", "ipad", "充电宝", "u盘", "优盘", "硬盘",
                "移动硬盘", "手表", "电子词典", "计算器", "蓝牙音箱", "音箱", "相机", "数码相机",
                "数据线", "充电器", "充电线", "鼠标", "键盘", "switch", "无人机", "mp3", "mp4",
                "录音笔", "智能手表", "电子表");
        map("雨伞", "伞", "折叠伞", "遮阳伞", "长柄伞", "晴雨伞", "自动伞");

        // 大类分组（用于类别语义距离的"同类"判定）
        group("数码电子", "手机", "电脑/电子设备", "耳机", "眼镜");
        group("服饰箱包", "衣物", "钱包/包", "书包/背包", "雨伞");
        group("文具证件", "书籍", "证件/卡");
        group("生活用品", "水杯", "钥匙");
    }

    private static void map(String canonical, String... aliases) {
        ALIAS.put(TextNormalizer.normalize(canonical), canonical);
        for (String a : aliases) {
            ALIAS.put(TextNormalizer.normalize(a), canonical);
        }
    }

    private static void group(String group, String... categories) {
        for (String c : categories) {
            GROUP.put(c, group);
        }
    }

    /**
     * 归一化类别名：文本规范化 + 别名映射；未命中则原样返回（trim 后）。
     */
    public static String normalize(String category) {
        if (category == null) {
            return "";
        }
        String s = TextNormalizer.normalize(category);
        if (s.isEmpty()) {
            return "";
        }
        String mapped = ALIAS.get(s);
        return mapped != null ? mapped : category.trim();
    }

    /**
     * 两个类别的语义相似度（0~1）：
     * 相等 1.0 / 包含 0.8 / 同类 0.7 / 无关 0.1。
     */
    public static double categorySimilarity(String ca, String cb) {
        String a = normalize(ca);
        String b = normalize(cb);
        if (a.isEmpty() || b.isEmpty()) {
            return 0.5;
        }
        if (a.equals(b)) {
            return 1.0;
        }
        if (a.contains(b) || b.contains(a)) {
            return 0.8;
        }
        String ga = GROUP.get(a);
        String gb = GROUP.get(b);
        if (ga != null && ga.equals(gb)) {
            return 0.7;
        }
        return 0.1;
    }
}
