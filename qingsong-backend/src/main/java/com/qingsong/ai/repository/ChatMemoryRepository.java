package com.qingsong.ai.repository;

import com.qingsong.ai.entity.vo.MessageVO;
import com.qingsong.ai.service.chat.ChatPersistenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository("chatMemoryRepositor")
@RequiredArgsConstructor
public class ChatMemoryRepository {

    private final ChatPersistenceService chatPersistenceService;

    public List<MessageVO> getChatMessageHistory(String type, String role, String chatId) {
        return chatPersistenceService.getChatMessages(type, role, chatId);
    }
}
