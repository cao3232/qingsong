package com.qingsong.ai.entity.po.chat;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_chat_session")
public class AiChatSession {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String sessionNo;

    private String bizType;

    private String roleCode;

    private String title;

    private String status;

    private Integer messageCount;

    private Long lastMessageId;

    private LocalDateTime lastMessageAt;

    private String lastUserMessageNo;

    private Integer deleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
