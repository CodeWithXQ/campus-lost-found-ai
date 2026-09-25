package com.campus.lostfound.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 认领记录
 */
@Data
@TableName("t_claim_record")
public class ClaimRecord {

    /** 待确认 */
    public static final String STATUS_PENDING = "PENDING";
    /** 已确认（认领成功，帖子下架） */
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    /** 已拒绝 */
    public static final String STATUS_REJECTED = "REJECTED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long matchId;

    /** 被认领的帖子 id（失物帖） */
    private Long postId;

    /** 申请人（失主） */
    private Long claimantId;

    private String message;

    /** PENDING / CONFIRMED / REJECTED */
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    // ===== 非数据库字段 =====
    @TableField(exist = false)
    private MatchRecord match;

    @TableField(exist = false)
    private String claimantName;

    @TableField(exist = false)
    private String postTitle;

    /** 拾主昵称（非数据库字段，填充招领帖发布者） */
    @TableField(exist = false)
    private String finderName;
}
