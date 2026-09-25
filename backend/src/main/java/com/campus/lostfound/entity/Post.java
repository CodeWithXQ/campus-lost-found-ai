package com.campus.lostfound.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 失物 / 招领帖子
 */
@Data
@TableName("t_post")
public class Post {

    /** 状态：展示中 */
    public static final int STATUS_OPEN = 0;
    /** 状态：已认领归档（认领闭环自动下架） */
    public static final int STATUS_CLAIMED = 1;
    /** 状态：已下架（手动下架） */
    public static final int STATUS_ARCHIVED = 2;
    /** 状态：待审核 */
    public static final int STATUS_PENDING = 3;
    /** 状态：审核未通过 */
    public static final int STATUS_REJECTED = 4;

    /** AI 初审核结论：通过 */
    public static final String AI_PASS = "PASS";
    /** AI 初审核结论：预警（需人工重点看） */
    public static final String AI_WARN = "WARN";
    /** AI 初审核结论：拒绝（命中硬性违禁） */
    public static final String AI_REJECT = "REJECT";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** LOST 失物 / FOUND 招领 */
    private String type;

    private String title;

    private String category;

    /** 丢失 / 拾获地点 */
    private String location;

    /** 丢失 / 拾获时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lostTime;

    private String description;

    /** 图片相对路径，逗号分隔（如 20260817/xxx.jpg,20260817/yyy.jpg） */
    private String imageUrls;

    /** AI 图像特征向量（逗号分隔的浮点数；Chinese-CLIP 512 维优先，降级 DINOv2/MobileNetV3），AI 服务不可用时为空 */
    private String featureVector;

    /** 状态：0 展示中 1 已认领归档 2 已下架 3 待审核 4 审核未通过 */
    private Integer status;

    /** AI 初审核结论：PASS / WARN / REJECT（null 表示未执行） */
    private String aiAuditResult;

    /** AI 初审核说明（风险点描述，供管理员参考） */
    private String aiAuditNote;

    /** 人工审核拒绝原因 */
    private String auditReason;

    /** AI 自动识别的物品类别（供前端参考，可被用户修改） */
    private String aiCategory;

    /** OCR 提取的关键号码（学号/卡号），用于证件卡类精确匹配 */
    private String ocrKey;

    /** 文本语义向量（Chinese-CLIP 文本塔，与图像同空间），用于语义匹配 / 文搜图 */
    private String textVector;

    /** 首图中文描述（BLIP 生成 + 英译中），用于匹配详情悬浮展示双方图片 AI 描述 */
    private String imageCaption;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    // ===== 非数据库字段（联表查询填充）=====
    @TableField(exist = false)
    private String ownerName;

    @TableField(exist = false)
    private String ownerAvatar;

    /** 匹配记录数（非数据库字段，我的发布列表填充） */
    @TableField(exist = false)
    private Integer matchCount;

    /** 最高匹配度 0~1（非数据库字段，无匹配时为 null） */
    @TableField(exist = false)
    private Double maxMatchScore;

    /** 最高匹配度的对方帖子（非数据库字段，我的发布列表用于展示指向） */
    @TableField(exist = false)
    private Post topMatchPost;
}
