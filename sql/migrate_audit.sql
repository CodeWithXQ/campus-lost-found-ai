-- =====================================================================
-- 迁移脚本：帖子表新增「发布审核 + AI 初审核」相关字段
-- 用途：普通用户发布后先进入管理员审核，审核通过才上线广场；AI 做初审核辅助。
-- 新增字段：
--   ai_audit_result  AI 初审核结论 PASS/WARN/REJECT（NULL=未执行）
--   ai_audit_note    AI 初审核说明（风险点描述，供管理员参考）
--   audit_reason     人工审核拒绝原因（落库）
-- 说明：若数据库尚未初始化，直接重新执行 init.sql 即可（已包含这些字段）；
--       若已初始化，请执行本脚本（不会清空数据）。
-- =====================================================================
USE lost_found;

ALTER TABLE t_post
    ADD COLUMN ai_audit_result VARCHAR(20)  DEFAULT NULL COMMENT 'AI初审核结论 PASS/WARN/REJECT' AFTER ai_category,
    ADD COLUMN ai_audit_note   VARCHAR(500) DEFAULT NULL COMMENT 'AI初审核说明（风险点描述）' AFTER ai_audit_result,
    ADD COLUMN audit_reason    VARCHAR(255) DEFAULT NULL COMMENT '人工审核拒绝原因' AFTER ai_audit_note;
