package com.qingsong.ai.entity.po.chat;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_chat_message")
public class AiChatMessage {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String messageNo;

    private Long sessionId;

    private Integer seqNo;

    private String messageType;

    private String content;

    private String contentFormat;

    private String toolCallsJson;

    private String metadataJson;

    private String status;

    private String requestId;

    private String errorMessage;

    private Integer deleted;

    private LocalDateTime deletedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private String chatModel;
}
