-- 聊天消息收藏表
-- 收藏即快照：content / 会话标题等在收藏时刻复制，
-- 原会话或原消息删除后收藏内容依然完整（副本语义），仅"查看原文"跳转能力随 sessionAlive 降级。
CREATE TABLE IF NOT EXISTS ai_chat_favorite
(
    id                 BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    user_id            BIGINT       NOT NULL COMMENT '收藏用户（sa-token loginId）',
    message_no         VARCHAR(64)  NOT NULL COMMENT '原消息业务ID',
    session_no         VARCHAR(64)  NOT NULL COMMENT '原会话业务ID（跳转原文用）',
    role_code          VARCHAR(128) NOT NULL COMMENT '角色编码',
    message_type       VARCHAR(32)  NOT NULL COMMENT '消息类型（USER/ASSISTANT）',
    content            LONGTEXT     NOT NULL COMMENT '消息内容快照',
    content_format     VARCHAR(16)  NOT NULL DEFAULT 'TEXT' COMMENT '内容格式快照',
    session_title      VARCHAR(255) NOT NULL DEFAULT '' COMMENT '会话标题快照',
    chat_model         VARCHAR(128) NULL COMMENT '生成该消息的模型快照',
    message_created_at DATETIME     NULL COMMENT '原消息时间',
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    UNIQUE KEY uk_user_message (user_id, message_no),
    KEY idx_user_created (user_id, created_at)
) COMMENT = '聊天消息收藏表';
