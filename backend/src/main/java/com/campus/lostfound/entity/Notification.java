package com.campus.lostfound.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内通知
 */
@Data
@TableName("t_notification")
public class Notification {

    /** 匹配成功 */
    public static final String TYPE_MATCH = "MATCH";
    /** 认领申请 */
    public static final String TYPE_CLAIM = "CLAIM";
    /** 系统通知 */
    public static final String TYPE_SYSTEM = "SYSTEM";
    /** 一键联系 */
    public static final String TYPE_CONTACT = "CONTACT";
    /** 审核结果通知 */
    public static final String TYPE_AUDIT = "AUDIT";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** MATCH / CLAIM / SYSTEM */
    private String type;

    private String title;

    private String content;

    /** 关联的匹配记录或帖子 id */
    private Long relatedId;

    /** 额外关联 id（如：一键联系时我的帖子 id，用于详情页只保留与我物品的匹配） */
    private Long extraId;

    /** 0 未读 1 已读 */
    private Integer isRead;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
