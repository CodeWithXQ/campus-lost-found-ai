package com.campus.lostfound.vo;

import com.campus.lostfound.entity.Post;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 匹配结果视图（含匹配依据拆解，用于前端展示"为什么匹配"）
 */
@Data
public class MatchVO {

    /** 匹配记录 id（未达阈值未生成记录时为 null） */
    private Long matchId;

    /** 匹配记录状态 */
    private String matchStatus;

    /** 发起查询的帖子 */
    private Post myPost;

    /** 匹配到的对方帖子 */
    private Post otherPost;

    /** 综合匹配分 */
    private Double finalScore;

    /** 纯文字匹配分 */
    private Double textScore;

    /** 图像相似度（无图像时为 null） */
    private Double imageScore;

    /** 名称相似度 */
    private Double nameScore;

    /** 类别匹配分 */
    private Double categoryScore;

    /** 地点匹配分 */
    private Double locationScore;

    /** 时间匹配分 */
    private Double timeScore;

    /** 描述匹配分（颜色/品牌/型号属性相似度） */
    private Double descriptionScore;

    /** 文本语义匹配分（Chinese-CLIP 文本向量余弦） */
    private Double semanticScore;

    /** 我方首图中文描述（BLIP 生成 + 英译中），用于悬浮展示 */
    private String myImageCaption;

    /** 对方首图中文描述（BLIP 生成 + 英译中），用于悬浮展示 */
    private String otherImageCaption;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
