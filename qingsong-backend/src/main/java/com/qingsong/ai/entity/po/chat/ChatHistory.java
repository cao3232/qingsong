package com.qingsong.ai.entity.po;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会话摘要对象。
 * 既用于前端会话列表展示，也用于 Redis 中缓存会话元数据，
 * 因此这里同时保留展示字段和持久化命中所需的最小元数据。
 */
@Data
@NoArgsConstructor
public class ChatHistory {

    /**
     * 业务侧会话 ID，对外暴露给前端使用。
     */
    private String id;

    /**
     * 兼容旧前端的名称字段，和 title 保持一致。
     */
    private String name;

    /**
     * 前端显示的会话标题。
     */
    private String title;

    /**
     * 当前会话绑定的角色名称/编码。
     */
    private String role;

    /**
     * 业务类型，例如 chat、service、pdf。
     */
    private String bizType;

    /**
     * MySQL 中 ai_chat_session 的主键，用于缓存命中时避免再查库。
     */
    private Long sessionDbId;

    /**
     * 当前会话消息总数。
     */
    private Integer messageCount;

    /**
     * 最后一条 USER 消息的业务ID，作为重试轮次标识(round_id)。
     */
    private String lastUserMessageNo;

    /**
     * 会话创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 最后一条消息时间，用于前端排序和展示。
     */
    private LocalDateTime lastMessageAt;

    /**
     * 标识该缓存项是否表示“会话存在”。
     * false 表示空值缓存，命中后可直接跳过数据库查询。
     */
    private Boolean exists = Boolean.TRUE;

    public ChatHistory(String id, String name) {
        this.id = id;
        this.name = name;
        this.title = name;
        this.exists = Boolean.TRUE;
    }

    public static ChatHistory missing(String sessionNo) {
        ChatHistory history = new ChatHistory();
        history.setId(sessionNo);
        history.setExists(Boolean.FALSE);
        return history;
    }
}
