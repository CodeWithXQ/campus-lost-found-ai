package com.campus.lostfound.dto;

import lombok.Data;

/**
 * 新增订阅请求
 */
@Data
public class SubscriptionDTO {

    /** 订阅的帖子类型 LOST/FOUND */
    private String type;

    /** 订阅类别（空=全部） */
    private String category;

    /** 订阅地点关键词（空=全部） */
    private String location;
}
