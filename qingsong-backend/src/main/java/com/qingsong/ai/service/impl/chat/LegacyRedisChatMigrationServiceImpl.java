package com.qingsong.ai.service.impl.chat;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.qingsong.ai.constants.RedisConstants;
import com.qingsong.ai.entity.po.ChatHistory;
import com.qingsong.ai.entity.po.chat.Msg;
import com.qingsong.ai.entity.po.chat.AiChatMessage;
import com.qingsong.ai.entity.po.chat.AiChatSession;
import com.qingsong.ai.entity.vo.chat.RedisMigrationSummary;
import com.qingsong.ai.mapper.chat.AiChatMessageMapper;
import com.qingsong.ai.mapper.chat.AiChatSessionMapper;
import com.qingsong.ai.service.chat.ChatCacheService;
import com.qingsong.ai.service.chat.LegacyRedisChatMigrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LegacyRedisChatMigrationServiceImpl implements LegacyRedisChatMigrationService {

    private static final String HISTORY_KEY_PATTERN = "user_role_history:*:*";

    private final StringRedisTemplate stringRedisTemplate;
    private final AiChatSessionMapper sessionMapper;
    private final AiChatMessageMapper messageMapper;
    private final ChatCacheService chatCacheService;

    @Override
    public RedisMigrationSummary migrateAll() {
        RedisMigrationSummary summary = new RedisMigrationSummary();
        Set<String> historyKeys = stringRedisTemplate.keys(HISTORY_KEY_PATTERN);
        if (CollectionUtils.isEmpty(historyKeys)) {
            return summary;
        }

        for (String historyKey : historyKeys) {
            HistoryKey parsed = parseHistoryKey(historyKey);
            if (parsed == null) {
                continue;
            }

            Map<Object, Object> sessions = stringRedisTemplate.opsForHash().entries(historyKey);
            if (CollectionUtils.isEmpty(sessions)) {
                continue;
            }

            for (Map.Entry<Object, Object> entry : sessions.entrySet()) {
                summary.setScannedSessionCount(summary.getScannedSessionCount() + 1);
                String sessionNo = String.valueOf(entry.getKey());
                ChatHistory chatHistory = parseChatHistory(sessionNo, entry.getValue());
                boolean migrated = migrateSingleSession(parsed.type(), parsed.role(), chatHistory, summary);
                if (migrated) {
                    summary.setMigratedSessionCount(summary.getMigratedSessionCount() + 1);
                } else {
                    summary.setSkippedSessionCount(summary.getSkippedSessionCount() + 1);
                }
            }
        }

        return summary;
    }

    @Transactional(rollbackFor = Exception.class)
    protected boolean migrateSingleSession(String bizType, String roleCode, ChatHistory chatHistory, RedisMigrationSummary summary) {
        if (chatHistory == null || !StringUtils.hasText(chatHistory.getId())) {
            return false;
        }

        AiChatSession existing = sessionMapper.selectOne(new QueryWrapper<AiChatSession>()
                .eq("session_no", chatHistory.getId())
                .last("limit 1"));
        if (existing != null) {
            return false;
        }

        LocalDateTime baseTime = resolveBaseTime(chatHistory.getId());
        AiChatSession session = new AiChatSession();
        session.setSessionNo(chatHistory.getId());
        session.setBizType(bizType);
        session.setRoleCode(roleCode);
        session.setTitle(buildTitle(chatHistory, roleCode));
        session.setStatus("ACTIVE");
        session.setMessageCount(0);
        session.setDeleted(0);
        session.setCreatedAt(baseTime);
        session.setUpdatedAt(baseTime);
        sessionMapper.insert(session);

        String messageKey = String.format(RedisConstants.USER_ROLE_HISTORY_MESSAGE_KEY.getRedisKey(), roleCode, chatHistory.getId());
        List<String> messageJsonList = stringRedisTemplate.opsForList().range(messageKey, 0, -1);
        if (!CollectionUtils.isEmpty(messageJsonList)) {
            AiChatMessage lastMessage = null;
            int migratedMessageCount = 0;
            for (int i = 0; i < messageJsonList.size(); i++) {
                Msg legacyMessage = parseLegacyMessage(chatHistory.getId(), i, messageJsonList.get(i));
                if (legacyMessage == null || legacyMessage.getMessageType() == null) {
                    continue;
                }
                migratedMessageCount++;
                AiChatMessage persisted = buildAiChatMessage(session.getId(), migratedMessageCount, legacyMessage, baseTime.plusSeconds(i));
                messageMapper.insert(persisted);
                lastMessage = persisted;
                summary.setMigratedMessageCount(summary.getMigratedMessageCount() + 1);
            }

            if (migratedMessageCount > 0 && lastMessage != null) {
                session.setMessageCount(migratedMessageCount);
                session.setLastMessageId(lastMessage.getId());
                session.setLastMessageAt(lastMessage.getCreatedAt());
                session.setUpdatedAt(lastMessage.getCreatedAt());
                sessionMapper.updateById(session);
            }
        }

        chatCacheService.evictSessionCaches(chatHistory.getId());
        log.info("迁移 Redis 会话完成, bizType={}, roleCode={}, sessionNo={}", bizType, roleCode, chatHistory.getId());
        return true;
    }

    private AiChatMessage buildAiChatMessage(Long sessionId, int seqNo, Msg legacyMessage, LocalDateTime createdAt) {
        AiChatMessage message = new AiChatMessage();
        message.setMessageNo(UUID.randomUUID().toString().replace("-", ""));
        message.setSessionId(sessionId);
        message.setSeqNo(seqNo);
        message.setMessageType(legacyMessage.getMessageType().name());
        message.setContent(legacyMessage.getText());
        message.setContentFormat("ASSISTANT".equalsIgnoreCase(legacyMessage.getMessageType().name()) ? "MARKDOWN" : "TEXT");
        message.setToolCallsJson(CollectionUtils.isEmpty(legacyMessage.getToolCalls()) ? null : JSON.toJSONString(legacyMessage.getToolCalls()));
        message.setMetadataJson(CollectionUtils.isEmpty(legacyMessage.getMetadata()) ? null : JSON.toJSONString(legacyMessage.getMetadata()));
        message.setStatus("SUCCESS");
        message.setDeleted(0);
        message.setCreatedAt(createdAt);
        message.setUpdatedAt(createdAt);
        return message;
    }

    private HistoryKey parseHistoryKey(String historyKey) {
        if (!StringUtils.hasText(historyKey)) {
            return null;
        }
        String[] parts = historyKey.split(":");
        if (parts.length < 3) {
            return null;
        }
        return new HistoryKey(parts[1], parts[2]);
    }

    private ChatHistory parseChatHistory(String sessionNo, Object rawValue) {
        if (rawValue == null) {
            return new ChatHistory(sessionNo, sessionNo);
        }
        String json = String.valueOf(rawValue);
        try {
            ChatHistory parsed = JSON.parseObject(json, ChatHistory.class);
            if (parsed != null) {
                return parsed;
            }
        } catch (Exception e) {
            log.warn("解析旧会话记录失败，使用兜底标题, sessionNo={}", sessionNo, e);
        }
        return new ChatHistory(sessionNo, sessionNo);
    }

    private Msg parseLegacyMessage(String sessionNo, int index, String messageJson) {
        if (!StringUtils.hasText(messageJson)) {
            return null;
        }
        try {
            return JSON.parseObject(messageJson, Msg.class);
        } catch (Exception e) {
            log.warn("解析旧消息失败，跳过该条记录, sessionNo={}, index={}", sessionNo, index, e);
            return null;
        }
    }

    private String buildTitle(ChatHistory chatHistory, String roleCode) {
        String title = StringUtils.hasText(chatHistory.getName()) ? chatHistory.getName().trim() : roleCode;
        if (!StringUtils.hasText(title)) {
            return "迁移会话";
        }
        return title.length() > 255 ? title.substring(0, 255) : title;
    }

    private LocalDateTime resolveBaseTime(String sessionNo) {
        if (StringUtils.hasText(sessionNo) && sessionNo.chars().allMatch(Character::isDigit)) {
            try {
                long millis = Long.parseLong(sessionNo);
                if (millis > 1_000_000_000_000L) {
                    return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return LocalDateTime.now();
    }

    private record HistoryKey(String type, String role) {
    }
}
