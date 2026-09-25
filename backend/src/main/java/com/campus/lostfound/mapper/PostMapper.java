package com.campus.lostfound.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.lostfound.entity.Post;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface PostMapper extends BaseMapper<Post> {

    /**
     * 显式更新特征向量（可为 null 置空）。
     * 不能依赖 updateById：MyBatis-Plus 默认忽略 null 字段，删除图片后残留旧向量会导致图像匹配度不消失。
     */
    @Update("UPDATE t_post SET feature_vector = #{vector, jdbcType=VARCHAR} WHERE id = #{id}")
    int updateFeatureVector(@Param("id") Long id, @Param("vector") String vector);

    /**
     * 显式更新 AI 初审核结论（note 可为 null 置空）。
     * 不依赖 updateById：MyBatis-Plus 默认忽略 null 字段，编辑后 AI 结论由 WARN 转 PASS 时残留旧说明。
     */
    @Update("UPDATE t_post SET ai_audit_result = #{result, jdbcType=VARCHAR}, ai_audit_note = #{note, jdbcType=VARCHAR} WHERE id = #{id}")
    int updateAiAudit(@Param("id") Long id, @Param("result") String result, @Param("note") String note);

    /**
     * 显式更新人工拒绝原因（reason 可为 null 置空）。
     */
    @Update("UPDATE t_post SET audit_reason = #{reason, jdbcType=VARCHAR} WHERE id = #{id}")
    int updateAuditReason(@Param("id") Long id, @Param("reason") String reason);

    /**
     * 显式更新 OCR 关键号码（key 可为 null 置空）。
     */
    @Update("UPDATE t_post SET ocr_key = #{key, jdbcType=VARCHAR} WHERE id = #{id}")
    int updateOcrKey(@Param("id") Long id, @Param("key") String key);

    /**
     * 显式更新文本语义向量（vector 可为 null 置空）。
     */
    @Update("UPDATE t_post SET text_vector = #{vector, jdbcType=VARCHAR} WHERE id = #{id}")
    int updateTextVector(@Param("id") Long id, @Param("vector") String vector);

    /**
     * 显式更新首图中文描述（caption 可为 null 置空）。
     */
    @Update("UPDATE t_post SET image_caption = #{caption, jdbcType=VARCHAR} WHERE id = #{id}")
    int updateImageCaption(@Param("id") Long id, @Param("caption") String caption);
}
