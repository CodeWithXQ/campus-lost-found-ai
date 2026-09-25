-- =====================================================================
-- 私聊会话模型升级迁移脚本：二元组「用户对」→ 三元组「用户对 + 帖子」
-- 旧格式 conversation_id 形如 "2_3"（仅一个下划线），新格式 "2_3_34"
-- 将旧数据补上帖子ID（无帖子记 0），使会话按物品区分
-- 执行方式：mysql -u root -p < migrate_chat_conversation_v2.sql
-- =====================================================================
USE lost_found;

UPDATE t_chat_message
SET conversation_id = CONCAT(conversation_id, '_', COALESCE(post_id, 0))
WHERE CHAR_LENGTH(conversation_id) - CHAR_LENGTH(REPLACE(conversation_id, '_', '')) = 1;
