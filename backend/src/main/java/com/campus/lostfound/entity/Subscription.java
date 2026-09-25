package com.campus.lostfound.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订阅（主动触达：用户订阅某类型/类别/地点的帖子，新帖子发布时推送提醒）
 */
@Data
@TableName("t_subscription")
public class Subscription {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 订阅的帖子类型 LOST/FOUND */
    private String type;

    /** 订阅类别（空=全部） */
    private String category;

    /** 订阅地点关键词（空=全部） */
    private String location;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
