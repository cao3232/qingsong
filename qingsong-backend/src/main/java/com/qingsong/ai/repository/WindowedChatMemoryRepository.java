package com.qingsong.ai.repository;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * 为聊天窗口内存提供扩展能力，避免上层组件依赖具体的 Redis 实现。
 */
public interface WindowedChatMemoryRepository extends ChatMemoryRepository {

    List<Message> findRecentMessages(String conversationId, Integer lastN);

    int countMessages(String conversationId);

}
