CREATE TABLE IF NOT EXISTS ai_chat_session
(
    id
    BIGINT
    PRIMARY
    KEY
    AUTO_INCREMENT
    COMMENT
    '主键',
    session_no
    VARCHAR
(
    64
) NOT NULL COMMENT '会话业务ID',
    biz_type VARCHAR
(
    32
) NOT NULL DEFAULT 'chat' COMMENT '业务类型',
    role_code VARCHAR
(
    128
) NOT NULL COMMENT '角色编码',
    title VARCHAR
(
    255
) NOT NULL DEFAULT '' COMMENT '会话标题',
    status VARCHAR
(
    32
) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
    message_count INT NOT NULL DEFAULT 0 COMMENT '消息总数',
    last_message_id BIGINT NULL COMMENT '最后一条消息ID',
    last_message_at DATETIME NULL COMMENT '最后一条消息时间',
    last_user_message_no VARCHAR(64) NULL COMMENT '最后一轮用户消息ID(round_id)',
    deleted TINYINT
(
    1
) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_session_no
(
    session_no
),
    KEY idx_biz_role_lastmsg
(
    biz_type,
    role_code,
    last_message_at
),
    KEY idx_deleted_lastmsg
(
    deleted,
    last_message_at
)
    ) COMMENT='AI会话表';

CREATE TABLE IF NOT EXISTS ai_chat_message
(
    id
    BIGINT
    PRIMARY
    KEY
    AUTO_INCREMENT
    COMMENT
    '主键',
    message_no
    VARCHAR
(
    64
) NOT NULL COMMENT '消息业务ID',
    session_id BIGINT NOT NULL COMMENT '会话主键ID',
    seq_no INT NOT NULL COMMENT '会话内顺序',
    message_type VARCHAR
(
    32
) NOT NULL COMMENT '消息类型',
    content LONGTEXT NOT NULL COMMENT '消息内容',
    content_format VARCHAR
(
    16
) NOT NULL DEFAULT 'TEXT' COMMENT '内容格式',
    tool_calls_json LONGTEXT NULL COMMENT '工具调用原始数据',
    metadata_json LONGTEXT NULL COMMENT '元数据',
    status VARCHAR
(
    32
) NOT NULL DEFAULT 'SUCCESS' COMMENT '消息状态',
    request_id VARCHAR
(
    64
) NULL COMMENT '请求幂等ID',
    error_message TEXT NULL COMMENT '错误信息',
    chat_model VARCHAR(128) NULL DEFAULT NULL COMMENT '使用的聊天模型',
    deleted TINYINT
(
    1
) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    deleted_at DATETIME NULL COMMENT '删除时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_message_no
(
    message_no
),
    UNIQUE KEY uk_session_seq
(
    session_id,
    seq_no
),
    KEY idx_session_created
(
    session_id,
    created_at
),
    KEY idx_session_status
(
    session_id,
    status
),
    CONSTRAINT fk_ai_chat_message_session
    FOREIGN KEY
(
    session_id
) REFERENCES ai_chat_session
(
    id
)
    ) COMMENT='AI消息表';

CREATE TABLE IF NOT EXISTS chat_review
(
    id                     BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    review_date            DATE NOT NULL COMMENT '复盘日期',
    data_json              LONGTEXT NULL COMMENT '当日统计数据快照（summary/leaderboard/sessions）',
    essay                  LONGTEXT NULL COMMENT 'AI 随笔（季羡林风格）',
    session_summaries_json LONGTEXT NULL COMMENT '会话总结 JSON 数组',
    role_summaries_json    LONGTEXT NULL COMMENT '角色小结 JSON 数组',
    model                  VARCHAR(128) NULL COMMENT '生成模型名',
    status                 VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/DONE/FAILED',
    error_message          TEXT NULL COMMENT '失败原因',
    created_at             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_review_date (review_date)
) COMMENT='对话复盘记录表';
