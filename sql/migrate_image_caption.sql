-- =====================================================================
-- 图像描述（captioning）：给 t_post 新增 image_caption 字段
-- 用于匹配详情悬浮展示双方首图的 AI 中文描述（BLIP 生成 + OPUS-MT 英译中）
-- 安全说明：仅新增字段（ALTER TABLE ADD COLUMN），不影响现有数据。
-- 执行方式：mysql -uroot -p --default-character-set=utf8mb4 < migrate_image_caption.sql
-- =====================================================================
USE lost_found;

ALTER TABLE t_post
    ADD COLUMN image_caption VARCHAR(255) DEFAULT NULL
    COMMENT '首图中文描述（BLIP+英译中），用于匹配详情悬浮展示' AFTER text_vector;
