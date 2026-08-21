package com.qingsong.ai.repository;

import com.qingsong.ai.context.ChatContext;
import com.qingsong.ai.service.chat.ChatCacheService;
import com.qingsong.ai.service.chat.ChatPersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisBackedChatMemoryRepository implements WindowedChatMemoryRepository {

    private static final int DEFAULT_CONTEXT_SIZE = 30;

    private final ChatPersistenceService chatPersistenceService;
    private final ChatCacheService chatCacheService;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public List<String> findConversationIds() {
        return new ArrayList<>();
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        return findRecentMessages(conversationId, this.getContextSize());
    }

    private Integer getContextSize() {
        // return DEFAULT_CONTEXT_SIZE;
        int size = Optional.ofNullable(stringRedisTemplate.opsForValue().get("ai:chat:context:size")).map(Integer::valueOf).orElse(DEFAULT_CONTEXT_SIZE);
        return size;
    }

    @Override
    public List<Message> findRecentMessages(String conversationId, Integer lastN) {

        String sessionNo = resolveSessionNo(conversationId);
        if (!StringUtils.hasText(sessionNo)) {
            return new ArrayList<>();
        }
        int limit = lastN == null || lastN <= 0 ? DEFAULT_CONTEXT_SIZE : lastN;
        return new ArrayList<>(chatPersistenceService.getRecentMessages(sessionNo, limit));
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        if (CollectionUtils.isEmpty(messages)) {
            return;
        }
        String sessionNo = resolveSessionNo(conversationId);
        if (!StringUtils.hasText(sessionNo)) {
            return;
        }
        chatCacheService.cacheContextMessages(sessionNo, messages);
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        String sessionNo = resolveSessionNo(conversationId);
        if (!StringUtils.hasText(sessionNo)) {
            return;
        }
        chatCacheService.evictSessionCaches(sessionNo);
    }

    @Override
    public int countMessages(String conversationId) {
        return findRecentMessages(conversationId, DEFAULT_CONTEXT_SIZE).size();
    }


    private String resolveSessionNo(String conversationId) {
        String chatId = ChatContext.getCurrentChatId();
        if (StringUtils.hasText(chatId)) {
            return chatId;
        }
        if (!StringUtils.hasText(conversationId)) {
            return null;
        }
        if (!conversationId.contains(":")) {
            return conversationId;
        }
        String[] parts = conversationId.split(":");
        return parts.length >= 2 ? parts[1] : conversationId;
    }
}
