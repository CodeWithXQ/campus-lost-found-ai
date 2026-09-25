package com.campus.lostfound.dto;

import lombok.Data;

/**
 * 发送私聊消息请求体
 */
@Data
public class ChatSendDTO {

    /** 接收者用户ID */
    private Long receiverId;

    /** 消息内容 */
    private String content;

    /** 关联帖子ID（会话上下文，可空） */
    private Long postId;
}
