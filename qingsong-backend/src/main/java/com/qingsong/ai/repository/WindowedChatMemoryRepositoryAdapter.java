package com.qingsong.ai.repository;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 为普通 {@link ChatMemoryRepository} 提供窗口化查询适配能力。
 */
public class WindowedChatMemoryRepositoryAdapter implements WindowedChatMemoryRepository {

    private final ChatMemoryRepository delegate;

    public WindowedChatMemoryRepositoryAdapter(ChatMemoryRepository delegate) {
        Assert.notNull(delegate, "delegate cannot be null");
        this.delegate = delegate;
    }

    public static WindowedChatMemoryRepository adapt(ChatMemoryRepository delegate) {
        if (delegate instanceof WindowedChatMemoryRepository windowedChatMemoryRepository) {
            return windowedChatMemoryRepository;
        }
        return new WindowedChatMemoryRepositoryAdapter(delegate);
    }

    @Override
    public List<String> findConversationIds() {
        return delegate.findConversationIds();
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        return delegate.findByConversationId(conversationId);
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        delegate.saveAll(conversationId, messages);
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        delegate.deleteByConversationId(conversationId);
    }

    @Override
    public List<Message> findRecentMessages(String conversationId, Integer lastN) {
        List<Message> allMessages = delegate.findByConversationId(conversationId);
        if (CollectionUtils.isEmpty(allMessages)) {
            return List.of();
        }
        if (lastN == null || lastN <= 0 || lastN >= allMessages.size()) {
            return allMessages;
        }
        int startIndex = allMessages.size() - lastN;
        return new ArrayList<>(allMessages.subList(startIndex, allMessages.size()));
    }

    @Override
    public int countMessages(String conversationId) {
        List<Message> messages = delegate.findByConversationId(conversationId);
        return messages != null ? messages.size() : 0;
    }

}
