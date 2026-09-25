-- =====================================================================
-- 大数据量优化：补充关键索引（增量，不影响已有数据）
-- 执行方式：mysql -u root -p < migrate_index.sql
-- 说明：本脚本仅需执行一次。若重复执行，会因索引已存在而报错，可安全忽略。
-- =====================================================================
USE lost_found;

-- 帖子表：发布时间索引（发布趋势统计、列表按时间倒序排序）
ALTER TABLE t_post ADD INDEX idx_create_time (create_time);

-- 匹配记录表：状态索引（匹配成功率 = 已认领匹配 / 总匹配 的统计过滤）
ALTER TABLE t_match_record ADD INDEX idx_status (status);

-- 认领记录表：状态索引（认领管理按状态筛选）
ALTER TABLE t_claim_record ADD INDEX idx_status (status);

-- 通知表：用户 + 时间复合索引（消息列表按时间倒序分页）
ALTER TABLE t_notification ADD INDEX idx_user_create (user_id, create_time);
