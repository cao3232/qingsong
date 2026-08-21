package com.qingsong.ai.repository;

import com.qingsong.ai.entity.po.ChatHistory;
import com.qingsong.ai.service.chat.ChatPersistenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class InMemoryChatHistoryRepository implements ChatHistoryRepository {

    private final ChatPersistenceService chatPersistenceService;

    @Override
    public void checkAndSave(String type, String chatId) {
        chatPersistenceService.ensureSession(type, "default", chatId, chatId);
    }

    @Override
    public void checkAndSave(String type, String chatId, String role) {
        chatPersistenceService.ensureSession(type, role, chatId, role);
    }

    @Override
    public List<ChatHistory> getChatHistorys(String type, String role) {
        return chatPersistenceService.getChatHistories(type, role);
    }

    @Override
    public Boolean deleteChatHistory(String type, String role, String chatId) {
        return chatPersistenceService.deleteSession(type, role, chatId);
    }

    @Override
    public void updateChatHistoryName(String type, String role, String chatId, String chatHistoryName) {
        chatPersistenceService.updateSessionTitle(type, role, chatId, chatHistoryName);
    }
}
