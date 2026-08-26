package com.qingsong.ai.repository;

import com.qingsong.ai.context.ChatContextSizeHolder;
import com.qingsong.ai.context.ChatContext;
import com.qingsong.ai.context.UserContext;
import com.qingsong.ai.entity.po.ChatHistory;
import com.qingsong.ai.entity.po.user.UserConfig;
import com.qingsong.ai.service.chat.ChatCacheService;
import com.qingsong.ai.service.chat.ChatPersistenceService;
import com.qingsong.ai.utils.UserConfigUtils;
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
    private final ChatContextSizeHolder chatContextSizeHolder;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public List<String> findConversationIds() {
        return new ArrayList<>();
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        log.info("findByConversationId: conversationId={}", conversationId);
        String sessionNo = resolveSessionNo(conversationId);
        Integer contextSize = getEffectiveContextSize(sessionNo);
        return findRecentMessages(conversationId, contextSize);
    }

    private Integer getEffectiveContextSize(String sessionNo) {
        // 1. 优先使用会话级缓存（请求线程写入，Reactor 线程可用）
        if (sessionNo != null) {
            Integer holderSize = chatContextSizeHolder.getContextSize(sessionNo);
            if (holderSize != null) {
                log.info("使用会话缓存上下文窗口大小: sessionNo={}, size={}", sessionNo, holderSize);
                return holderSize;
            }
        }

        // 2. 当前登录用户的持久化配置
        try {
            UserConfig config = UserConfigUtils.getCurrentUserConfig();
            if (config != null && config.getContextSize() != null) {
                log.info("读取用户上下文窗口配置: userId={}, contextSize={}",
                        UserContext.getCurrentUserId(), config.getContextSize());
                return config.getContextSize();
            }
            log.info("用户未配置上下文窗口，使用默认: userId={}", UserContext.getCurrentUserId());
        } catch (Exception e) {
            log.warn("获取用户上下文窗口设置失败", e);
        }

        // 3. 兼容旧的全局 Redis 配置
        Integer redisSize = Optional.ofNullable(stringRedisTemplate.opsForValue().get("ai:chat:context:size"))
                .map(Integer::valueOf)
                .orElse(null);
        if (redisSize != null && redisSize > 0) {
            log.info("使用全局 Redis 上下文窗口配置: size={}", redisSize);
            return redisSize;
        }

        log.info("使用默认上下文窗口大小: size={}", DEFAULT_CONTEXT_SIZE);
        return DEFAULT_CONTEXT_SIZE;
    }

    @Override
    public List<Message> findRecentMessages(String conversationId, Integer lastN) {

        String sessionNo = resolveSessionNo(conversationId);
        log.debug("findRecentMessages: conversationId={}, sessionNo={}, lastN={}", conversationId, sessionNo, lastN);
        if (!StringUtils.hasText(sessionNo)) {
            return new ArrayList<>();
        }
        // lastN 由 findByConversationId 传入，已包含用户配置/session 缓存
        int limit = lastN == null ? DEFAULT_CONTEXT_SIZE : lastN;
        if (limit <= 0) {
            log.info("上下文窗口为 0，返回空列表");
            return new ArrayList<>();
        }

        // 1. 本地缓存命中：一请求内多次读取直接复用，避免重复查 Redis/DB
        List<Message> localCached = chatCacheService.getLocalContextMessages(sessionNo).orElse(null);
        if (!CollectionUtils.isEmpty(localCached)) {
            // 命中判断与 getRecentMessages 一致：need = min(limit, actual)
            // actual 取 ai:chat:session 缓存的 messageCount（读取路径已保证有值）
            int actual = chatCacheService.getSessionMeta(sessionNo)
                    .map(ChatHistory::getMessageCount)
                    .orElse(0);
            int need = Math.min(limit, actual);
            if (localCached.size() >= need) {
                if (localCached.size() > limit) {
                    List<Message> trimmed = new ArrayList<>(
                            localCached.subList(localCached.size() - limit, localCached.size()));
                    log.info("本地缓存命中(截断): sessionNo={}, cachedSize={}, limit={}, returned={}",
                            sessionNo, localCached.size(), limit, trimmed.size());
                    return trimmed;
                }
                log.info("本地缓存命中: sessionNo={}, cachedSize={}, limit={}, need={}",
                        sessionNo, localCached.size(), limit, need);
                return new ArrayList<>(localCached);
            }
            // 缓存条数不足（窗口变大），落到下层按实际数量补全
            log.debug("本地缓存条数不足，继续查下层: sessionNo={}, cachedSize={}, limit={}, actual={}",
                    sessionNo, localCached.size(), limit, actual);
        }

        // 2. 本地缓存未命中，走 Redis/DB（getRecentMessages 内部按 min(limit, actual) 判断）
        List<Message> messages = chatPersistenceService.getRecentMessages(sessionNo, limit);
        log.info("查询到上下文消息数: sessionNo={}, count={}", sessionNo, messages.size());
        chatCacheService.cacheLocalContextMessages(sessionNo, messages);
        return new ArrayList<>(messages);
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
        // 同步更新本地缓存，保证本次请求内后续读取能看到最新消息
        chatCacheService.cacheLocalContextMessages(sessionNo, messages);
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
