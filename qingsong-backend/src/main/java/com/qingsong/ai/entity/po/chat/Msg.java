package com.qingsong.ai.entity.po.chat;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class Msg {
    MessageType messageType;
    String text;
    Map<String, Object> metadata;
    List<AssistantMessage.ToolCall> toolCalls;

    public Msg(Message message) {
        this.messageType = message.getMessageType();
        this.text = message.getText();
        this.metadata = message.getMetadata();
        if (message instanceof AssistantMessage am) {
            this.toolCalls = am.getToolCalls();
        }
    }

    public Message toMessage() {
        return switch (messageType) {
            case SYSTEM -> new SystemMessage(text);
            case USER -> UserMessage.builder()
                    .text(text)
                    .media(List.of())
                    .metadata(metadata != null ? metadata : Collections.emptyMap())
                    .build();
            case ASSISTANT -> AssistantMessage
                    .builder()
                    .content(text)
                    .properties(metadata != null ? metadata : Collections.emptyMap())
                    .media(List.of())
                    .build();
            default -> throw new IllegalArgumentException("Unsupported message type: " + messageType);
        };
    }
}
