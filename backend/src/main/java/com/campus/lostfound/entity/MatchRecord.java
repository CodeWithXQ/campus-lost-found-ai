package com.campus.lostfound.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 匹配记录
 */
@Data
@TableName("t_match_record")
public class MatchRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long lostPostId;

    private Long foundPostId;

    /** 纯文字匹配分 0~1 */
    private Double textScore;

    /** 图像相似度 0~1，无图像时为空 */
    private Double imageScore;

    /** 综合匹配分 0~1 */
    private Double score;

    /** NEW 新匹配 / CONTACTED 已联系 / CLAIMED 已认领 / IGNORED 已忽略 */
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    // ===== 非数据库字段 =====
    @TableField(exist = false)
    private Post lostPost;

    @TableField(exist = false)
    private Post foundPost;

    // ===== 匹配依据六维分解（非数据库字段，认领详情实时重算填充，用于展示"为什么匹配"）=====
    @TableField(exist = false)
    private Double nameScore;

    @TableField(exist = false)
    private Double categoryScore;

    @TableField(exist = false)
    private Double locationScore;

    @TableField(exist = false)
    private Double timeScore;

    @TableField(exist = false)
    private Double descriptionScore;

    @TableField(exist = false)
    private Double semanticScore;
}
