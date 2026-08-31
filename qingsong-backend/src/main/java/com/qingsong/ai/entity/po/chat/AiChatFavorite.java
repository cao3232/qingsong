package com.qingsong.ai.entity.po.chat;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聊天消息收藏（快照语义）：收藏时刻复制消息内容与会话上下文，
 * 原消息/会话删除后收藏内容依然完整。
 */
@Data
@TableName("ai_chat_favorite")
public class AiChatFavorite {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 收藏用户（sa-token loginId） */
    private Long userId;

    /** 原消息业务ID */
    private String messageNo;

    /** 原会话业务ID（跳转原文用） */
    private String sessionNo;

    /** 角色编码 */
    private String roleCode;

    /** 消息类型（USER/ASSISTANT） */
    private String messageType;

    /** 消息内容快照 */
    private String content;

    /** 内容格式快照 */
    private String contentFormat;

    /** 会话标题快照 */
    private String sessionTitle;

    /** 生成该消息的模型快照 */
    private String chatModel;

    /** 原消息时间 */
    private LocalDateTime messageCreatedAt;

    /** 收藏时间 */
    private LocalDateTime createdAt;
}
