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
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
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

    private static final String RETRY_STALE_CODE = "CHAT_RETRY_STALE";

    private static final String SESSION_DELETED_CODE = "CHAT_SESSION_DELETED";
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
    public void appendUserMessage(String bizType, String roleCode, String sessionNo, String content, String messageNo) {
        AiChatSession session = getOrCreateSession(bizType, roleCode, sessionNo, content);
        // 防抖幂等：同一 (session, messageNo) 的用户消息已落库（双击/网络重发）则跳过，避免重复写入
        if (StringUtils.hasText(messageNo) && existsUserMessage(session.getId(), messageNo)) {
            return;
        }
        // 空会话的首条消息：标题默认取该消息内容（会话由 /chat/pre 预建时标题为 roleCode，这里补齐"标题=首条消息"）
        boolean emptySession = session.getMessageCount() == null || session.getMessageCount() == 0;
        if (emptySession) {
            session.setTitle(buildSessionTitle(content, session.getRoleCode()));
        }
        AiChatMessage persisted = persistMessage(session, buildUserMessage(content), MESSAGE_STATUS_SUCCESS, null, null, messageNo);
        updateSessionAfterMessage(session, persisted);
        // 添加到缓存中
        chatCacheService.appendActiveMessage(sessionNo, toMessage(persisted));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void appendAssistantMessage(String bizType, String roleCode, String sessionNo, String content, String signalType, String chatModel) {
        if (!StringUtils.hasText(content)) {
            return;
        }

        log.debug("追加助手消息: sessionNo={}, signalType={}, contentLength={}", sessionNo, signalType, content.length());

        AiChatSession session = getOrCreateSession(bizType, roleCode, sessionNo, roleCode);
        Message assistantMessage = new AssistantMessage(content);
        AiChatMessage persisted = persistMessage(session, assistantMessage, normalizeStatus(signalType), isSuccessStatus(signalType) ? null : content, chatModel, null);
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
        // limit <= 0 表示无上下文（含负数兜底），直接返回空列表
        if (limit <= 0) {
            return new ArrayList<>();
        }

        List<Message> cachedMessages = chatCacheService.getContextMessages(sessionNo).orElse(null);
        if (!CollectionUtils.isEmpty(cachedMessages)) {
            // 用"实际数量"（ai:chat:session 缓存的 messageCount，读取路径保证缓存有值）判断缓存是否足够
            AiChatSession session = findSessionBySessionNo(sessionNo, true, true);
            int actual = (session != null && session.getMessageCount() != null) ? session.getMessageCount() : 0;
            int need = Math.min(limit, actual);
            if (cachedMessages.size() >= need) {
                if (cachedMessages.size() > limit) {
                    // 窗口变小，缓存比需要的多，截取最近 limit 条（拷贝，避免视图污染）
                    return new ArrayList<>(cachedMessages.subList(cachedMessages.size() - limit, cachedMessages.size()));
                }
                // 缓存刚好或会话本身消息较少，整体返回（拷贝）
                return new ArrayList<>(cachedMessages);
            }
            // 缓存不足（窗口变大 / 缓存过期残留），回 DB 补全
            log.debug("会话缓存消息数不足，回数据库查询: sessionNo={}, cachedSize={}, need={}, limit={}",
                    sessionNo, cachedMessages.size(), need, limit);
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
    public Map<String, String> selectChatModelByIds(List<String> messageNos) {
        QueryWrapper<AiChatMessage> queryWrapper = new QueryWrapper<AiChatMessage>()
                .in("message_no", messageNos)
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
            // 会话已被删除（另一窗口删除）：拒绝继续写入，避免静默"复活"撤销删除
            if (isDeleted(existing)) {
                throw new BusinessException("会话已删除，无法继续发送", SESSION_DELETED_CODE);
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

    private AiChatMessage persistMessage(AiChatSession session, Message message, String status, String errorMessage, String chatModel, String messageNo) {
        LocalDateTime now = LocalDateTime.now();
        AiChatMessage entity = new AiChatMessage();
        entity.setMessageNo(StringUtils.hasText(messageNo) ? messageNo : generateMessageNo());
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

    private boolean existsUserMessage(Long sessionId, String messageNo) {
        Long count = messageMapper.selectCount(new QueryWrapper<AiChatMessage>()
                .eq("session_id", sessionId)
                .eq("message_no", messageNo)
                .eq("deleted", 0));
        return count != null && count > 0;
    }

    private void updateSessionAfterMessage(AiChatSession session, AiChatMessage message) {
        session.setMessageCount((session.getMessageCount() == null ? 0 : session.getMessageCount()) + 1);
        session.setLastMessageId(message.getId());
        session.setLastMessageAt(message.getCreatedAt());
        if ("USER".equalsIgnoreCase(message.getMessageType())) {
            session.setLastUserMessageNo(message.getMessageNo());
        }
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
        history.setLastUserMessageNo(session.getLastUserMessageNo());
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
        session.setLastUserMessageNo(sessionMeta.getLastUserMessageNo());
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

    /**
     * 删除"最后一轮"（最后一条消息，若为 AI 回复则连带前一条用户消息），并重算会话计数。
     *
     * <p>⚠️ 该"删最后一轮"业务规则在 Redis 缓存层有<b>另一份</b>实现：
     * {@link ChatCacheServiceImpl#deleteLastRound}（removeLastRound）。
     * 两者必须保持一致——新增消息类型（如工具消息）时，两处同步修改，否则 DB 与缓存会不一致。</p>
     */
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
        // 同步刷新最后一条 USER 消息 ID，保证重试校验基于最新轮次
        AiChatMessage lastUser = messageMapper.selectOne(
                new QueryWrapper<AiChatMessage>()
                        .eq("session_id", session.getId())
                        .eq("deleted", 0)
                        .eq("message_type", "USER")
                        .orderByDesc("seq_no")
                        .last("LIMIT 1")
        );
        session.setLastUserMessageNo(lastUser != null ? lastUser.getMessageNo() : null);
        session.setUpdatedAt(LocalDateTime.now());
        sessionMapper.updateById(session);
        // 同步刷新 Redis 会话元数据缓存，避免重试后基于过期的 messageCount 累加导致计数虚高
        refreshSessionMetaCache(session);
    }

    @Override
    public void validateRetry(String sessionNo, String messageNo) {
        AiChatSession session = findSessionBySessionNo(sessionNo, false, false);
        if (session == null || isDeleted(session)) {
            throw new BusinessException("会话不存在，无法重试");
        }
        // 前端无后端消息ID：仅当后端也没有任何用户消息记录（刚发送、未落库）时允许重试
        if (!StringUtils.hasText(messageNo)) {
            if (session.getLastUserMessageNo() == null) {
                return;
            }
            throw new BusinessException("会话最后一条消息已变化，无法重试", RETRY_STALE_CODE);
        }
        AiChatMessage target = messageMapper.selectOne(
                new QueryWrapper<AiChatMessage>()
                        .eq("session_id", session.getId())
                        .eq("message_no", messageNo)
                        .eq("deleted", 0)
                        .last("LIMIT 1")
        );
        // 后端无此记录：仅当会话尚未落库任何用户消息（请求在落库前失败）时允许；否则会话已有轮次，删轮会误删
        if (target == null) {
            if (session.getLastUserMessageNo() == null) {
                return;
            }
            throw new BusinessException("会话最后一条消息已变化，无法重试", RETRY_STALE_CODE);
        }
        if (!"USER".equalsIgnoreCase(target.getMessageType())) {
            throw new BusinessException("未找到可重试的用户消息");
        }
        // 已落库的：必须是最后一轮的用户消息；否则会话已推进，禁止重试
        if (!messageNo.equals(session.getLastUserMessageNo())) {
            throw new BusinessException("会话最后一条消息已变化，无法重试", RETRY_STALE_CODE);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void retryLastRound(String bizType, String roleCode, String sessionNo, String messageNo, String content) {
        validateRetry(sessionNo, messageNo);
        // 校验通过后才清理 Redis 消息缓存，避免校验失败时缓存已被截断导致重新拉取丢失最新消息
        chatCacheService.deleteLastRound(sessionNo);
        deleteLastRound(sessionNo);
        appendUserMessage(bizType, roleCode, sessionNo, content, messageNo);
    }
    private boolean isDeleted(AiChatSession session) {
        return session.getDeleted() != null && session.getDeleted() == 1;
    }

    private String generateMessageNo() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String normalizeStatus(String signalType) {
        log.debug("normalizeStatus 入参: signalType={}", signalType);
        if (!StringUtils.hasText(signalType)) {
            return MESSAGE_STATUS_SUCCESS;
        }
        // 兼容 onCancel / cancel 两种写法
        if ("onCancel".equalsIgnoreCase(signalType) || "cancel".equalsIgnoreCase(signalType)) {
            return "CANCELLED";
        }
        if ("onError".equalsIgnoreCase(signalType) || "error".equalsIgnoreCase(signalType)) {
            return "ERROR";
        }
        return MESSAGE_STATUS_SUCCESS;
    }

    private boolean isSuccessStatus(String signalType) {
        return "SUCCESS".equalsIgnoreCase(normalizeStatus(signalType));
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
