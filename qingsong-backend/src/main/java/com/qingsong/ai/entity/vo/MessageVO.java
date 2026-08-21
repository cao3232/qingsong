package com.qingsong.ai.entity.vo;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;

@NoArgsConstructor
@Data
public class MessageVO {
    private String id;
    private String role;
    private String content;
    private LocalDateTime createdAt;
    private String chatModel;

    public MessageVO(Message message) {
        switch (message.getMessageType()) {
            case USER:
                role = "user";
                break;
            case ASSISTANT:
                role = "assistant";
                break;
            default:
                role = "";
                break;
        }
        this.content = message.getText();
        hydrateFromMetadata(message.getMetadata());
    }

    private void hydrateFromMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return;
        }

        Object messageId = metadata.get("messageId");
        if (messageId != null) {
            this.id = String.valueOf(messageId);
        }

        Object createdAtValue = metadata.get("createdAt");
        if (createdAtValue instanceof LocalDateTime localDateTime) {
            this.createdAt = localDateTime;
            return;
        }
        if (!(createdAtValue instanceof String createdAtText) || !StringUtils.hasText(createdAtText)) {
            return;
        }

        try {
            this.createdAt = LocalDateTime.parse(createdAtText);
        } catch (DateTimeParseException ignored) {
            // Redis 中历史缓存允许兼容旧数据，无法解析时直接忽略时间字段。
        }
    }

}
