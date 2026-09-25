-- =====================================================================
-- 阶段六：文本语义 + 文搜图 - 给 t_post 新增 text_vector 字段
-- 安全说明：仅新增字段（ALTER TABLE ADD COLUMN），不影响现有数据。
-- 执行方式：mysql -uroot -p --default-character-set=utf8mb4 < migrate_text_vector.sql
-- =====================================================================
USE lost_found;

ALTER TABLE t_post
    ADD COLUMN text_vector TEXT DEFAULT NULL
    COMMENT '文本语义向量（Chinese-CLIP文本塔，与图像同空间），用于语义匹配/文搜图' AFTER ocr_key;
