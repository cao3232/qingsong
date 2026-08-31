package com.qingsong.ai.entity.vo.chat;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息内容搜索命中项（消息粒度，snippet 为关键词上下文片段）。
 */
@Data
public class ChatSearchHitVO {
    private String sessionNo;
    private String sessionTitle;
    private String messageNo;
    private String messageType;
    private String snippet;
    private LocalDateTime createdAt;
}
