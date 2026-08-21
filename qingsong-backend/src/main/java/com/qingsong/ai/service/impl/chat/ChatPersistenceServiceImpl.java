package com.qingsong.ai.service.impl.chat;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.qingsong.ai.entity.exception.BusinessException;
import com.qingsong.ai.entity.po.ChatHistory;
import com.qingsong.ai.entity.po.chat.AiChatMessage;
import com.qingsong.ai.entity.po.chat.AiChatSession;
import com.qingsong.ai.entity.vo.MessageVO;
import com.qingsong.ai.mapper.chat.AiChatMessageMapper;
import com.qingsong.ai.mapper.chat.AiChatSessionMapper;
import com.qingsong.ai.service.chat.ChatCacheService;
import com.qingsong.ai.service.chat.ChatPersistenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.util.Collections.*;

@Service
@RequiredArgsConstructor
public class ChatPersistenceServiceImpl implements ChatPersistenceService {

    private static final int DEFAULT_CONTEXT_SIZE = 30;
    private static final String SESSION_STATUS_ACTIVE = "ACTIVE";
    private static final String MESSAGE_STATUS_SUCCESS = "SUCCESS";
    private static final String CONTENT_FORMAT_TEXT = "TEXT";
    private static final String CONTENT_FORMAT_MARKDOWN = "MARKDOWN";
    private static final String MESSAGE_METADATA_ID = "messageId";
    private static final String MESSAGE_METADATA_CREATED_AT = "createdAt";

    private final AiChatSessionMapper sessionMapper;
    private final AiChatMessageMapper messageMapper;
    private final ChatCacheService chatCacheService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void ensureSession(String bizType, String roleCode, String sessionNo, String title) {
        AiChatSession session = getOrCreateSession(bizType, roleCode, sessionNo, title);
        refreshSessionMetaCache(session);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void appendUserMessage(String bizType, String roleCode, String sessionNo, String content) {
        AiChatSession session = getOrCreateSession(bizType, roleCode, sessionNo, content);
        AiChatMessage persisted = persistMessage(session, buildUserMessage(content), MESSAGE_STATUS_SUCCESS, null, null);
        updateSessionAfterMessage(session, persisted);
        // 添加到缓存中
        chatCacheService.appendActiveMessage(sessionNo, toMessage(persisted));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void appendAssistantMessage(String bizType, String roleCode, String sessionNo, String content, String status, String chatModel) {
        if (!StringUtils.hasText(content)) {
            return;
        }

        AiChatSession session = getOrCreateSession(bizType, roleCode, sessionNo, roleCode);
        Message assistantMessage = new AssistantMessage(content);
        AiChatMessage persisted = persistMessage(session, assistantMessage, normalizeStatus(status), isSuccessStatus(status) ? null : content, chatModel);
        updateSessionAfterMessage(session, persisted);

        chatCacheService.appendActiveMessage(sessionNo, toMessage(persisted));
    }

    @Override
    public List<ChatHistory> getChatHistories(String bizType, String roleCode) {
        List<AiChatSession> sessions = sessionMapper.selectList(new QueryWrapper<AiChatSession>()
                .eq("biz_type", bizType)
                .eq("role_code", roleCode)
                .eq("deleted", 0)
                .orderByDesc("last_message_at")
                .orderByDesc("created_at"));

        if (CollectionUtils.isEmpty(sessions)) {
            return List.of();
        }

        return sessions.stream()
                .map(this::toChatHistory)
                .toList();
    }

    @Override
    public List<MessageVO> getChatMessages(String bizType, String roleCode, String sessionNo) {
        List<Message> activeMessages = chatCacheService.getActiveMessages(sessionNo).orElse(null);
        if (!CollectionUtils.isEmpty(activeMessages)) {
            return activeMessages.stream().map(MessageVO::new).toList();
        }

        AiChatSession session = findSessionBySessionNo(sessionNo, true, true);
        if (session == null || isDeleted(session)) {
            return List.of();
        }

        List<AiChatMessage> messages = messageMapper.selectList(new QueryWrapper<AiChatMessage>()
                .eq("session_id", session.getId())
                .eq("deleted", 0)
                .orderByAsc("seq_no"));
        if (CollectionUtils.isEmpty(messages)) {
            return List.of();
        }

        chatCacheService.cacheActiveMessages(sessionNo, messages.stream().map(this::toMessage).toList());
        return messages.stream().map(this::toMessageVO).toList();
    }

    @Override
    public List<Message> getAllMessages(String sessionNo) {
        AiChatSession session = findSessionBySessionNo(sessionNo, true, true);
        if (session == null || isDeleted(session)) {
            return List.of();
        }

        List<AiChatMessage> messages = messageMapper.selectList(new QueryWrapper<AiChatMessage>()
                .eq("session_id", session.getId())
                .eq("deleted", 0)
                .orderByAsc("seq_no"));

        return messages.stream().map(this::toMessage).toList();
    }

    @Override
    public List<Message> getRecentMessages(String sessionNo, int limit) {
        List<Message> cachedMessages = chatCacheService.getContextMessages(sessionNo).orElse(null);
        if (!CollectionUtils.isEmpty(cachedMessages)) {
            if (limit <= 0 || cachedMessages.size() <= limit) {
                return cachedMessages;
            }
            return cachedMessages.subList(cachedMessages.size() - limit, cachedMessages.size());
        }

        AiChatSession session = findSessionBySessionNo(sessionNo, true, true);
        if (session == null || isDeleted(session)) {
            return List.of();
        }

        int finalLimit = limit > 0 ? limit : DEFAULT_CONTEXT_SIZE;
        List<AiChatMessage> messages = messageMapper.selectList(new QueryWrapper<AiChatMessage>()
                .eq("session_id", session.getId())
                .eq("deleted", 0)
                .orderByDesc("seq_no")
                .last("limit " + finalLimit));

        if (CollectionUtils.isEmpty(messages)) {
            return List.of();
        }

        List<Message> result = new ArrayList<>(messages.stream()
                .map(this::toMessage)
                .toList());
        reverse(result);
        chatCacheService.cacheContextMessages(sessionNo, result);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteSession(String bizType, String roleCode, String sessionNo) {
        AiChatSession session = findSessionBySessionNo(sessionNo, true, false);
        if (session == null || isDeleted(session)) {
            return false;
        }

        session.setDeleted(1);
        session.setStatus("DELETED");
        session.setUpdatedAt(LocalDateTime.now());
        int updated = sessionMapper.updateById(session);
        if (updated > 0) {
            chatCacheService.evictSessionCaches(sessionNo);
            return true;
        }
        return false;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSessionTitle(String bizType, String roleCode, String sessionNo, String title) {
        AiChatSession session = findSessionBySessionNo(sessionNo, true, false);
        if (session == null || isDeleted(session) || !StringUtils.hasText(title)) {
            return;
        }

        session.setTitle(title.trim());
        session.setUpdatedAt(LocalDateTime.now());
        sessionMapper.updateById(session);
        refreshSessionMetaCache(session);
    }

    @Override
    public Map<String, String> selectChatModelByIds(List<String> assistantIds) {
        QueryWrapper<AiChatMessage> queryWrapper = new QueryWrapper<AiChatMessage>()
                .in("message_no", assistantIds)
                .eq("deleted", 0)
                .eq("message_type", "ASSISTANT")
                .select("message_no", "chat_model");
        List<AiChatMessage> aiChatMessages = this.messageMapper.selectList(queryWrapper);
        if (CollectionUtils.isEmpty(aiChatMessages)) {
            return Map.of();
        }
        return aiChatMessages.stream().collect(Collectors.toMap(AiChatMessage::getMessageNo, AiChatMessage::getChatModel));
    }

    private AiChatSession getOrCreateSession(String bizType, String roleCode, String sessionNo, String defaultTitle) {
        AiChatSession existing = findSessionBySessionNo(sessionNo, false, false);
        if (existing != null) {
            if (isDeleted(existing)) {
                existing.setDeleted(0);
                existing.setStatus(SESSION_STATUS_ACTIVE);
                existing.setTitle(buildSessionTitle(defaultTitle, roleCode));
                existing.setUpdatedAt(LocalDateTime.now());
                sessionMapper.updateById(existing);
            }
            return existing;
        }

        LocalDateTime now = LocalDateTime.now();
        AiChatSession session = new AiChatSession();
        session.setSessionNo(sessionNo);
        session.setBizType(defaultIfBlank(bizType, "chat"));
        session.setRoleCode(roleCode);
        session.setTitle(buildSessionTitle(defaultTitle, roleCode));
        session.setStatus(SESSION_STATUS_ACTIVE);
        session.setMessageCount(0);
        session.setDeleted(0);
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        sessionMapper.insert(session);
        return session;
    }

    private AiChatSession findSessionBySessionNo(String sessionNo) {
        return findSessionBySessionNo(sessionNo, true, true);
    }

    private AiChatSession findSessionBySessionNo(String sessionNo, boolean cacheMissing, boolean refreshMetaCache) {
        ChatHistory cachedSessionMeta = chatCacheService.getSessionMeta(sessionNo).orElse(null);
        if (cachedSessionMeta != null) {
            if (Boolean.FALSE.equals(cachedSessionMeta.getExists())) {
                return null;
            }
            if (cachedSessionMeta.getSessionDbId() != null) {
                return toSessionFromCache(cachedSessionMeta);
            }
        }

        AiChatSession session = sessionMapper.selectOne(new QueryWrapper<AiChatSession>()
                .eq("session_no", sessionNo)
                .last("limit 1"));
        if (session == null) {
            if (cacheMissing) {
                chatCacheService.cacheSessionMeta(sessionNo, ChatHistory.missing(sessionNo));
            }
            return null;
        }
        if (refreshMetaCache) {
            refreshSessionMetaCache(session);
        }
        return session;
    }

    private AiChatMessage persistMessage(AiChatSession session, Message message, String status, String errorMessage, String chatModel) {
        LocalDateTime now = LocalDateTime.now();
        AiChatMessage entity = new AiChatMessage();
        entity.setMessageNo(generateMessageNo());
        entity.setSessionId(session.getId());
        entity.setSeqNo(nextSequence(session.getId()));
        entity.setMessageType(message.getMessageType().name());
        entity.setContent(message.getText());
        entity.setContentFormat(message instanceof AssistantMessage ? CONTENT_FORMAT_MARKDOWN : CONTENT_FORMAT_TEXT);
        entity.setToolCallsJson(extractToolCalls(message));
        entity.setMetadataJson(extractMetadata(message));
        entity.setStatus(status);
        entity.setErrorMessage(errorMessage);
        entity.setDeleted(0);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setChatModel(chatModel);
        messageMapper.insert(entity);
        return entity;
    }

    private int nextSequence(Long sessionId) {
        AiChatMessage latest = messageMapper.selectOne(new QueryWrapper<AiChatMessage>()
                .eq("session_id", sessionId)
                .orderByDesc("seq_no")
                .last("limit 1"));
        return latest == null ? 1 : latest.getSeqNo() + 1;
    }

    private void updateSessionAfterMessage(AiChatSession session, AiChatMessage message) {
        session.setMessageCount((session.getMessageCount() == null ? 0 : session.getMessageCount()) + 1);
        session.setLastMessageId(message.getId());
        session.setLastMessageAt(message.getCreatedAt());
        session.setUpdatedAt(LocalDateTime.now());
        sessionMapper.updateById(session);
        refreshSessionMetaCache(session);
    }

    private void refreshSessionMetaCache(AiChatSession session) {
        chatCacheService.cacheSessionMeta(session.getSessionNo(), toChatHistory(session));
    }

    private ChatHistory toChatHistory(AiChatSession session) {
        ChatHistory history = new ChatHistory(session.getSessionNo(), session.getTitle());
        history.setTitle(session.getTitle());
        history.setName(session.getTitle());
        history.setRole(session.getRoleCode());
        history.setBizType(session.getBizType());
        history.setSessionDbId(session.getId());
        history.setMessageCount(session.getMessageCount());
        history.setCreatedAt(session.getCreatedAt());
        history.setLastMessageAt(session.getLastMessageAt());
        history.setExists(Boolean.TRUE);
        return history;
    }

    private AiChatSession toSessionFromCache(ChatHistory sessionMeta) {
        AiChatSession session = new AiChatSession();
        session.setId(sessionMeta.getSessionDbId());
        session.setSessionNo(sessionMeta.getId());
        session.setBizType(sessionMeta.getBizType());
        session.setRoleCode(sessionMeta.getRole());
        session.setTitle(sessionMeta.getTitle());
        session.setMessageCount(sessionMeta.getMessageCount());
        session.setDeleted(0);
        session.setCreatedAt(sessionMeta.getCreatedAt());
        session.setLastMessageAt(sessionMeta.getLastMessageAt());
        return session;
    }

    private Message toMessage(AiChatMessage message) {
        Map<String, Object> metadata = buildMessageMetadata(message);
        if ("USER".equalsIgnoreCase(message.getMessageType())) {
            return UserMessage.builder()
                    .text(message.getContent())
                    .media(List.of())
                    .metadata(metadata)
                    .build();
        } else if ("SYSTEM".equalsIgnoreCase(message.getMessageType())) {
            return SystemMessage.builder()
                    .text(message.getContent())
                    .metadata(metadata)
                    .build();
        }
        return AssistantMessage.builder()
                .properties(metadata)
                .content(message.getContent())
                .build();
    }

    private MessageVO toMessageVO(AiChatMessage message) {
        MessageVO messageVO = new MessageVO(toMessage(message));
        messageVO.setId(message.getMessageNo());
        messageVO.setCreatedAt(message.getCreatedAt());
        messageVO.setChatModel(message.getChatModel());
        return messageVO;
    }

    private String extractToolCalls(Message message) {
        if (message instanceof AssistantMessage assistantMessage && !CollectionUtils.isEmpty(assistantMessage.getToolCalls())) {
            return JSON.toJSONString(assistantMessage.getToolCalls());
        }
        return null;
    }

    private String extractMetadata(Message message) {
        if (!CollectionUtils.isEmpty(message.getMetadata())) {
            return JSON.toJSONString(message.getMetadata());
        }
        return null;
    }

    private Message buildUserMessage(String content) {
        return UserMessage.builder().text(content).media(List.of()).build();
    }

    private Map<String, Object> buildMessageMetadata(AiChatMessage message) {
        Map<String, Object> metadata = new HashMap<>();
        if (StringUtils.hasText(message.getMessageNo())) {
            metadata.put(MESSAGE_METADATA_ID, message.getMessageNo());
        }
        if (message.getCreatedAt() != null) {
            metadata.put(MESSAGE_METADATA_CREATED_AT, message.getCreatedAt().toString());
        }
        return metadata;
    }

    private String buildSessionTitle(String defaultTitle, String roleCode) {
        String base = StringUtils.hasText(defaultTitle) ? defaultTitle.trim() : roleCode;
        if (!StringUtils.hasText(base)) {
            return "新会话";
        }
        return base.length() > 30 ? base.substring(0, 30) : base;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteLastRound(String sessionNo) {
        AiChatSession session = findSessionBySessionNo(sessionNo, false, false);
        if (session == null) {
            return;
        }

        List<AiChatMessage> lastTwo = messageMapper.selectList(
                new QueryWrapper<AiChatMessage>()
                        .eq("session_id", session.getId())
                        .eq("deleted", 0)
                        .orderByDesc("seq_no")
                        .last("LIMIT 2")
        );

        if (CollectionUtils.isEmpty(lastTwo)) {
            return;
        }

        AiChatMessage lastMessage = lastTwo.get(0);

        if ("ASSISTANT".equalsIgnoreCase(lastMessage.getMessageType())) {
            // 情况1：最后一条是 AI 回复，删除 A，并删除前一条 U
            messageMapper.deleteById(lastMessage.getId());
            if (lastTwo.size() > 1) {
                AiChatMessage secondLast = lastTwo.get(1);
                if ("USER".equalsIgnoreCase(secondLast.getMessageType())) {
                    messageMapper.deleteById(secondLast.getId());
                }
            }
        } else if ("USER".equalsIgnoreCase(lastMessage.getMessageType())) {
            // 情况2：最后一条是用户消息，只删除 U
            messageMapper.deleteById(lastMessage.getId());
        }

        // 刷新 session 的 messageCount 和 lastMessage
        refreshSessionAfterDelete(session);
    }

    private void refreshSessionAfterDelete(AiChatSession session) {
        // 统计剩余消息数
        Long count = messageMapper.selectCount(
                new QueryWrapper<AiChatMessage>()
                        .eq("session_id", session.getId())
                        .eq("deleted", 0)
        );
        session.setMessageCount(count != null ? count.intValue() : 0);

        // 更新 lastMessage 信息
        AiChatMessage newLast = messageMapper.selectOne(
                new QueryWrapper<AiChatMessage>()
                        .eq("session_id", session.getId())
                        .eq("deleted", 0)
                        .orderByDesc("seq_no")
                        .last("LIMIT 1")
        );
        if (newLast != null) {
            session.setLastMessageId(newLast.getId());
            session.setLastMessageAt(newLast.getCreatedAt());
        } else {
            session.setLastMessageId(null);
            session.setLastMessageAt(null);
        }
        session.setUpdatedAt(LocalDateTime.now());
        sessionMapper.updateById(session);
        // 同步刷新 Redis 会话元数据缓存，避免重试后基于过期的 messageCount 累加导致计数虚高
        refreshSessionMetaCache(session);
    }

    @Override
    public void validateRetry(String sessionNo, String messageId, String prompt) {
        AiChatSession session = findSessionBySessionNo(sessionNo, false, false);
        if (session == null || isDeleted(session)) {
            throw new BusinessException("会话不存在，无法重试");
        }
        AiChatMessage lastMessage = messageMapper.selectOne(
                new QueryWrapper<AiChatMessage>()
                        .eq("session_id", session.getId())
                        .eq("deleted", 0)
                        .orderByDesc("seq_no")
                        .last("LIMIT 1")
        );
        if (lastMessage == null) {
            throw new BusinessException("会话最后一条消息不存在，无法重试");
        }

        // 定位最后一条 USER 消息（重试/编辑的目标轮次）
        AiChatMessage lastUser = lastMessage;
        if ("ASSISTANT".equalsIgnoreCase(lastMessage.getMessageType())) {
            lastUser = messageMapper.selectOne(
                    new QueryWrapper<AiChatMessage>()
                            .eq("session_id", session.getId())
                            .eq("deleted", 0)
                            .lt("seq_no", lastMessage.getSeqNo())
                            .orderByDesc("seq_no")
                            .last("LIMIT 1")
            );
        }
        if (lastUser == null || !"USER".equalsIgnoreCase(lastUser.getMessageType())) {
            throw new BusinessException("未找到可重试的用户消息");
        }

        // 优先：messageId 与最后一条 USER 消息的 messageNo 一致（历史消息有服务端 ID）
        if (StringUtils.hasText(messageId) && messageId.equals(lastUser.getMessageNo())) {
            return;
        }

        // 兜底：比对最后一条 USER 消息的内容（兼容当前会话内新建、无服务端 messageNo 的消息）
        if (StringUtils.hasText(prompt) && prompt.equals(lastUser.getContent())) {
            return;
        }

        throw new BusinessException("会话最后一条消息已变化，无法重试");
    }
    private boolean isDeleted(AiChatSession session) {
        return session.getDeleted() != null && session.getDeleted() == 1;
    }

    private String generateMessageNo() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return MESSAGE_STATUS_SUCCESS;
        }
        if ("cancel".equalsIgnoreCase(status)) {
            return "CANCELLED";
        }
        if ("onError".equalsIgnoreCase(status) || "error".equalsIgnoreCase(status)) {
            return "ERROR";
        }
        return MESSAGE_STATUS_SUCCESS;
    }

    private boolean isSuccessStatus(String status) {
        return "SUCCESS".equalsIgnoreCase(normalizeStatus(status));
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
