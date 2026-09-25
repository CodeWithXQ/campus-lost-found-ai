-- =====================================================================
-- 阶段四：OCR 证件卡匹配 - 给 t_post 新增 ocr_key 字段
-- 安全说明：仅新增字段（ALTER TABLE ADD COLUMN），不影响现有数据。
-- 执行方式：mysql -uroot -p --default-character-set=utf8mb4 < migrate_ocr.sql
-- =====================================================================
USE lost_found;

ALTER TABLE t_post
    ADD COLUMN ocr_key VARCHAR(64) DEFAULT NULL
    COMMENT 'OCR提取的关键号码（学号/卡号），用于证件卡类精确匹配' AFTER ai_category;
