package com.campus.lostfound.dto;

import lombok.Data;

/**
 * 发布 / 编辑帖子请求
 */
@Data
public class PostDTO {

    /** LOST 失物 / FOUND 招领 */
    private String type;

    private String title;

    private String category;

    /** 丢失 / 拾获地点 */
    private String location;

    /** 丢失 / 拾获时间（yyyy-MM-dd HH:mm:ss 或 yyyy-MM-dd） */
    private String lostTime;

    private String description;

    /** 图片相对路径，逗号分隔 */
    private String imageUrls;

    /** AI 自动识别的类别（可为空，由用户自行选择） */
    private String aiCategory;
}
