package com.campus.lostfound.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 私聊会话列表项
 */
@Data
public class ChatVO {

    /** 对方用户ID */
    private Long peerId;

    /** 对方昵称 */
    private String peerName;

    /** 对方头像 */
    private String peerAvatar;

    /** 会话关联的帖子ID（物品上下文，可空） */
    private Long postId;

    /** 会话关联的帖子标题（物品名称） */
    private String postTitle;

    /** 最后一条消息内容 */
    private String lastContent;

    /** 最后一条消息时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastTime;

    /** 未读消息数 */
    private int unread;
}
