package com.qingsong.ai.service.chat;

import com.qingsong.ai.entity.po.ChatHistory;
import org.springframework.ai.chat.messages.Message;

import java.util.List;
import java.util.Optional;

public interface ChatCacheService {

    Optional<ChatHistory> getSessionMeta(String sessionNo);

    void cacheSessionMeta(String sessionNo, ChatHistory chatHistory);

    void evictSessionMeta(String sessionNo);

    Optional<List<Message>> getActiveMessages(String sessionNo);

    void cacheActiveMessages(String sessionNo, List<Message> messages);

    void appendActiveMessage(String sessionNo, Message message);

    Optional<List<Message>> getContextMessages(String sessionNo);

    void cacheContextMessages(String sessionNo, List<Message> messages);

    Optional<List<Message>> getLocalContextMessages(String sessionNo);

    void cacheLocalContextMessages(String sessionNo, List<Message> messages);

    void evictSessionCaches(String sessionNo);

    void deleteLastRound(String sessionNo);


}
