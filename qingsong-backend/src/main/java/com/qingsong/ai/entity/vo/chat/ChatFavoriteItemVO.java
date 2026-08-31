package com.qingsong.ai.entity.vo.chat;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 收藏列表项：消息快照 + 原会话存活标记（sessionAlive=false 时前端置灰"查看原文"）。
 */
@Data
public class ChatFavoriteItemVO {

    /** 原消息业务ID（同时是取消收藏与跳转原文的锚点） */
    private String messageNo;

    /** 原会话业务ID */
    private String sessionNo;

    /** 角色编码 */
    private String roleCode;

    /** 消息类型（USER/ASSISTANT） */
    private String messageType;

    /** 消息内容快照（收藏时刻的副本，不随原消息删除） */
    private String content;

    /** 会话标题快照 */
    private String sessionTitle;

    /** 生成该消息的模型快照 */
    private String chatModel;

    /** 原消息时间 */
    private LocalDateTime messageCreatedAt;

    /** 收藏时间 */
    private LocalDateTime createdAt;

    /** 收藏主键（游标分页第二排序键） */
    private Long favoriteId;

    /** 原会话是否仍存在（存在才允许"查看原文"跳转） */
    private Boolean sessionAlive;

    /** 原消息是否仍存在（消息被删时即使会话还在，跳转也定位不到，前端置灰"查看原文"） */
    private Boolean messageAlive;
}
