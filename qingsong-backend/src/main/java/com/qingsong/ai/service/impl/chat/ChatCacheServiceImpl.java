package com.qingsong.ai.service.impl.chat;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.qingsong.ai.entity.po.ChatHistory;
import com.qingsong.ai.entity.po.chat.Msg;
import com.qingsong.ai.service.chat.ChatCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatCacheServiceImpl implements ChatCacheService {

    private static final Duration SESSION_META_TTL = Duration.ofHours(6);
    private static final Duration ACTIVE_MESSAGES_TTL = Duration.ofHours(2);
    private static final Duration CONTEXT_MESSAGES_TTL = Duration.ofHours(24);

    private static final String SESSION_META_KEY = "ai:chat:session:%s";
    private static final String ACTIVE_MESSAGES_KEY = "ai:chat:active:%s";
    private static final String CONTEXT_MESSAGES_KEY = "ai:chat:ctx:%s";

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public Optional<ChatHistory> getSessionMeta(String sessionNo) {
        String json = stringRedisTemplate.opsForValue().get(formatKey(SESSION_META_KEY, sessionNo));
        if (!StringUtils.hasText(json)) {
            return Optional.empty();
        }
        return Optional.ofNullable(JSON.parseObject(json, ChatHistory.class));
    }

    @Override
    public void cacheSessionMeta(String sessionNo, ChatHistory chatHistory) {
        if (chatHistory == null) {
            return;
        }
        stringRedisTemplate.opsForValue().set(
                formatKey(SESSION_META_KEY, sessionNo),
                JSON.toJSONString(chatHistory),
                SESSION_META_TTL
        );
    }

    @Override
    public void evictSessionMeta(String sessionNo) {
        stringRedisTemplate.delete(formatKey(SESSION_META_KEY, sessionNo));
    }

    @Override
    public Optional<List<Message>> getActiveMessages(String sessionNo) {
        return getMessages(ACTIVE_MESSAGES_KEY, sessionNo);
    }

    @Override
    public Optional<List<Message>> getContextMessages(String sessionNo) {
        return getMessages(CONTEXT_MESSAGES_KEY, sessionNo);
    }

    @Override
    public void cacheActiveMessages(String sessionNo, List<Message> messages) {
        cacheMessages(ACTIVE_MESSAGES_KEY, sessionNo, messages, ACTIVE_MESSAGES_TTL);
    }

    @Override
    public void appendActiveMessage(String sessionNo, Message message) {
        List<Message> messages = getActiveMessages(sessionNo).orElseGet(ArrayList::new);
        messages.add(message);
        cacheActiveMessages(sessionNo, messages);
    }


    @Override
    public void cacheContextMessages(String sessionNo, List<Message> messages) {
        cacheMessages(CONTEXT_MESSAGES_KEY, sessionNo, messages, CONTEXT_MESSAGES_TTL);
    }

    @Override
    public void appendContextMessages(String sessionNo, List<Message> messages, int maxSize) {
        if (CollectionUtils.isEmpty(messages)) {
            return;
        }
        List<Message> existing = getContextMessages(sessionNo).orElseGet(ArrayList::new);
        existing.addAll(messages);
        int startIndex = Math.max(0, existing.size() - maxSize);
        cacheContextMessages(sessionNo, new ArrayList<>(existing.subList(startIndex, existing.size())));
    }

    @Override
    public void evictSessionCaches(String sessionNo) {
        stringRedisTemplate.delete(List.of(
                formatKey(SESSION_META_KEY, sessionNo),
                formatKey(ACTIVE_MESSAGES_KEY, sessionNo),
                formatKey(CONTEXT_MESSAGES_KEY, sessionNo)
        ));
    }

    /**
     * 删除"最后一轮"（最后一条消息，若为 AI 回复则连带前一条用户消息）。
     *
     * <p>⚠️ 该"删最后一轮"业务规则在 DB 持久化层有<b>另一份</b>实现：
     * {@link ChatPersistenceServiceImpl#deleteLastRound}。
     * 两者必须保持一致——新增消息类型（如工具消息）时，两处同步修改，否则 DB 与缓存会不一致。</p>
     */
    @Override
    public void deleteLastRound(String sessionNo) {
        clearLastRoundFromCache(sessionNo, this::getContextMessages, this::cacheContextMessages);
        clearLastRoundFromCache(sessionNo, this::getActiveMessages, this::cacheActiveMessages);
    }

    private void clearLastRoundFromCache(
            String sessionNo,
            Function<String, Optional<List<Message>>> getter,
            BiConsumer<String, List<Message>> saver) {
        getter.apply(sessionNo).ifPresent(original -> {
            List<Message> messages = new ArrayList<>(original);
            removeLastRound(messages);
            saver.accept(sessionNo, messages);
        });
    }

    private void removeLastRound(List<Message> messages) {
        if (messages.isEmpty()) {
            return;
        }

        Message last = messages.get(messages.size() - 1);
        messages.remove(messages.size() - 1);

        if (last instanceof AssistantMessage && !messages.isEmpty()) {
            Message secondLast = messages.get(messages.size() - 1);
            if (secondLast instanceof UserMessage) {
                messages.remove(messages.size() - 1);
            }
        }
    }

    private Optional<List<Message>> getMessages(String keyPattern, String sessionNo) {
        String json = stringRedisTemplate.opsForValue().get(formatKey(keyPattern, sessionNo));
        if (!StringUtils.hasText(json)) {
            return Optional.empty();
        }

        List<Msg> cached = JSON.parseObject(json, new TypeReference<List<Msg>>() {
        });
        if (CollectionUtils.isEmpty(cached)) {
            return Optional.of(new ArrayList<>());
        }

        return Optional.of(new ArrayList<>(cached.stream().map(Msg::toMessage).toList()));
    }

    private void cacheMessages(String keyPattern, String sessionNo, List<Message> messages, Duration ttl) {
        List<Msg> payload = CollectionUtils.isEmpty(messages)
                ? List.of()
                : messages.stream().map(Msg::new).toList();
        stringRedisTemplate.opsForValue().set(formatKey(keyPattern, sessionNo), JSON.toJSONString(payload), ttl);
    }

    private String formatKey(String keyPattern, String sessionNo) {
        log.info("格式化缓存键: {}", String.format(keyPattern, sessionNo));
        return String.format(keyPattern, sessionNo);
    }
}
