package com.qingsong.ai.repository;

import com.qingsong.ai.repository.WindowedChatMemoryRepository;
import com.qingsong.ai.repository.WindowedChatMemoryRepositoryAdapter;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;

/**
 * 自定义消息窗口聊天内存实现，支持Redis优化
 *
 * @author : caojiangjiang
 * @data : 2026/02/24 20:21
 */
public class MyMessageWindowChatMemory implements ChatMemory {

    private static final int DEFAULT_MAX_MESSAGES = 20;
    private final WindowedChatMemoryRepository chatMemoryRepository;
    private final int maxMessages;

    private MyMessageWindowChatMemory(WindowedChatMemoryRepository chatMemoryRepository, int maxMessages) {
        Assert.notNull(chatMemoryRepository, "chatMemoryRepository cannot be null");
        Assert.isTrue(maxMessages > 0, "maxMessages must be greater than 0");
        this.chatMemoryRepository = chatMemoryRepository;
        this.maxMessages = maxMessages;
    }

    public void add(String conversationId, List<Message> messages) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        Assert.notNull(messages, "messages cannot be null");
        Assert.noNullElements(messages, "messages cannot contain null elements");

        List<Message> memoryMessages = this.chatMemoryRepository.findByConversationId(conversationId);
        List<Message> processedMessages = this.process(memoryMessages, messages);
        if (processedMessages.isEmpty()) {
            return;
        }
        this.chatMemoryRepository.saveAll(conversationId, processedMessages);
    }

    public List<Message> get(String conversationId) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        return this.chatMemoryRepository.findByConversationId(conversationId);
    }

    /**
     * 获取会话中的最后N条消息
     *
     * @param conversationId 会话ID
     * @param lastN          要获取的消息数量，如果为null或小于等于0则返回所有消息
     * @return 最近的N条消息列表
     */
    public List<Message> get(String conversationId, Integer lastN) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        return this.chatMemoryRepository.findRecentMessages(conversationId, lastN);
    }

    /**
     * 获取会话中的消息总数
     *
     * @param conversationId 会话ID
     * @return 消息总数
     */
    public int count(String conversationId) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        return this.chatMemoryRepository.countMessages(conversationId);
    }

    public void clear(String conversationId) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        this.chatMemoryRepository.deleteByConversationId(conversationId);
    }

    private List<Message> process(List<Message> memoryMessages, List<Message> newMessages) {
        List<Message> processedMessages = new ArrayList<>(memoryMessages);
        boolean hasNewSystemMessage = newMessages.stream().anyMatch(SystemMessage.class::isInstance);

        if (hasNewSystemMessage) {
            processedMessages.removeIf(SystemMessage.class::isInstance);
        }

        for (Message newMessage : newMessages) {
            if (isDuplicateUserMessage(processedMessages, newMessage)) {
                continue;
            }
            processedMessages.add(newMessage);
        }

        if (processedMessages.size() <= this.maxMessages) {
            return processedMessages;
        }

        int messagesToRemove = processedMessages.size() - this.maxMessages;
        List<Message> trimmedMessages = new ArrayList<>(processedMessages.size() - messagesToRemove);
        int removed = 0;
        for (Message message : processedMessages) {
            if (!(message instanceof SystemMessage) && removed < messagesToRemove) {
                removed++;
                continue;
            }
            trimmedMessages.add(message);
        }
        return trimmedMessages;
    }

    private boolean isDuplicateUserMessage(List<Message> existingMessages, Message newMessage) {
        if (!(newMessage instanceof UserMessage) || existingMessages.isEmpty()) {
            return false;
        }
        Message latestMessage = existingMessages.get(existingMessages.size() - 1);
        return latestMessage instanceof UserMessage
                && newMessage.getText().equals(latestMessage.getText());
    }

    public static Builder builder() {
        return new Builder();
    }



    public static final class Builder {
        private ChatMemoryRepository chatMemoryRepository;
        private int maxMessages = DEFAULT_MAX_MESSAGES;

        private Builder() {
        }

        public Builder chatMemoryRepository(ChatMemoryRepository chatMemoryRepository) {
            this.chatMemoryRepository = chatMemoryRepository;
            return this;
        }

        public Builder maxMessages(int maxMessages) {
            this.maxMessages = maxMessages;
            return this;
        }

        public MyMessageWindowChatMemory build() {
            if (this.chatMemoryRepository == null) {
                this.chatMemoryRepository = new InMemoryChatMemoryRepository();
            }

            return new MyMessageWindowChatMemory(
                    WindowedChatMemoryRepositoryAdapter.adapt(this.chatMemoryRepository),
                    this.maxMessages
            );
        }
    }
}
