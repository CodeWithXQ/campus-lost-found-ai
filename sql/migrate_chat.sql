-- =====================================================================
-- 私聊消息表迁移脚本（增量，不影响已有数据）
-- 执行方式：mysql -u root -p < migrate_chat.sql
-- =====================================================================
USE lost_found;

CREATE TABLE IF NOT EXISTS t_chat_message (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    conversation_id VARCHAR(64)  NOT NULL COMMENT '会话ID（用户对+帖子三元组，如 2_3_34，无帖子记 0）',
    sender_id       BIGINT       NOT NULL COMMENT '发送者ID',
    receiver_id     BIGINT       NOT NULL COMMENT '接收者ID',
    content         VARCHAR(500) NOT NULL COMMENT '消息内容',
    post_id         BIGINT       DEFAULT NULL COMMENT '关联帖子ID（会话上下文，可空）',
    is_read         TINYINT      NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
    create_time     DATETIME     DEFAULT NULL COMMENT '发送时间',
    KEY idx_conversation (conversation_id, create_time),
    KEY idx_receiver (receiver_id, is_read)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='私聊消息表';
