package com.qingsong.ai.service.impl.chat;

import com.qingsong.ai.config.MyRolesConfig;
import com.qingsong.ai.entity.po.ChatHistory;
import com.qingsong.ai.entity.po.role.Role;
import com.qingsong.ai.entity.vo.MessageVO;
import com.qingsong.ai.entity.vo.chat.ChatHistoryPageVO;
import com.qingsong.ai.entity.vo.chat.ChatSearchHitVO;
import com.qingsong.ai.mapper.chat.AiChatMessageMapper;
import com.qingsong.ai.mapper.chat.AiChatSessionMapper;
import com.qingsong.ai.repository.ChatMemoryRepository;
import com.qingsong.ai.service.chat.ChatFavoriteService;
import com.qingsong.ai.service.chat.ChatHistoryService;
import com.qingsong.ai.service.chat.ChatPersistenceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * description...
 *
 * @author : caojiangjiang
 * @data : 2026/04/28 10:28
 */
@Service
public class ChatHistoryServiceImpl implements ChatHistoryService {
    @Autowired
    private ChatMemoryRepository chatMemoryRepository;
    @Autowired
    private ChatPersistenceService chatPersistenceService;
    @Autowired
    private MyRolesConfig myRolesConfig;
    @Autowired
    private AiChatSessionMapper aiChatSessionMapper;
    @Autowired
    private AiChatMessageMapper aiChatMessageMapper;
    @Autowired
    private ChatFavoriteService chatFavoriteService;

    static final int DEFAULT_PAGE_SIZE = 15;
    static final int MAX_PAGE_SIZE = 200;
    static final int MAX_KEYWORD_LENGTH = 50;
    private static final int SNIPPET_CONTEXT_BEFORE = 40;
    private static final int SNIPPET_LENGTH = 120;

    static int clampPageSize(int limit) {
        if (limit <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(limit, MAX_PAGE_SIZE);
    }

    /**
     * LIKE 特殊字符转义（配合 SQL 中 ESCAPE '!'）；空白关键词返回 null 表示不过滤。
     */
    static String escapeLike(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        String trimmed = keyword.trim();
        if (trimmed.length() > MAX_KEYWORD_LENGTH) {
            trimmed = trimmed.substring(0, MAX_KEYWORD_LENGTH);
        }
        return trimmed.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    @Override
    public ChatHistoryPageVO getChatHistoryPage(String type, String role, String keyword,
                                                LocalDateTime start, LocalDateTime end,
                                                LocalDateTime before, Long beforeId, int limit) {
        int pageSize = clampPageSize(limit);
        // 多取 1 条用于判断 hasMore
        List<Map<String, Object>> rows = aiChatSessionMapper.selectPageByCursor(
                type, role, escapeLike(keyword), start, end, before, beforeId, pageSize + 1);
        boolean hasMore = rows.size() > pageSize;
        if (hasMore) {
            rows = new ArrayList<>(rows.subList(0, pageSize));
        }
        List<ChatHistory> list = rows.stream().map(this::rowToChatHistory).toList();
        return new ChatHistoryPageVO(list, hasMore);
    }

    @Override
    public List<String> getChatHistoryDates(String type, String role) {
        return aiChatSessionMapper.selectActiveDates(type, role);
    }

    @Override
    public List<ChatSearchHitVO> searchChatMessages(String type, String role, String keyword,
                                                    LocalDateTime start, LocalDateTime end, int limit) {
        String escaped = escapeLike(keyword);
        if (escaped == null) {
            return List.of();
        }
        List<Map<String, Object>> rows = aiChatMessageMapper.searchMessages(
                type, role, escaped, start, end, clampPageSize(limit));
        return rows.stream().map(this::rowToSearchHit).toList();
    }

    private ChatHistory rowToChatHistory(Map<String, Object> row) {
        ChatHistory history = new ChatHistory();
        history.setId(Objects.toString(row.get("id"), null));
        history.setTitle(Objects.toString(row.get("title"), ""));
        history.setName(Objects.toString(row.get("title"), ""));
        history.setRole(Objects.toString(row.get("role"), null));
        history.setBizType(Objects.toString(row.get("bizType"), null));
        Object dbId = row.get("sessionDbId");
        history.setSessionDbId(dbId == null ? null : ((Number) dbId).longValue());
        Object mc = row.get("messageCount");
        history.setMessageCount(mc == null ? 0 : ((Number) mc).intValue());
        history.setLastUserMessageNo(Objects.toString(row.get("lastUserMessageNo"), null));
        history.setCreatedAt(toLocalDateTime(row.get("createdAt")));
        history.setLastMessageAt(toLocalDateTime(row.get("lastMessageAt")));
        history.setExists(Boolean.TRUE);
        return history;
    }

    private ChatSearchHitVO rowToSearchHit(Map<String, Object> row) {
        ChatSearchHitVO hit = new ChatSearchHitVO();
        hit.setSessionNo(Objects.toString(row.get("sessionNo"), null));
        hit.setSessionTitle(Objects.toString(row.get("sessionTitle"), ""));
        hit.setMessageNo(Objects.toString(row.get("messageNo"), null));
        hit.setMessageType(Objects.toString(row.get("messageType"), ""));
        hit.setCreatedAt(toLocalDateTime(row.get("createdAt")));
        String snippet = Objects.toString(row.get("snippet"), "");
        int hitIndex = row.get("hitIndex") == null ? 0 : ((Number) row.get("hitIndex")).intValue();
        int contentLength = row.get("contentLength") == null ? 0 : ((Number) row.get("contentLength")).intValue();
        int snippetStart = Math.max(1, hitIndex - SNIPPET_CONTEXT_BEFORE);
        String prefix = snippetStart > 1 ? "…" : "";
        String suffix = (snippetStart + SNIPPET_LENGTH - 1) < contentLength ? "…" : "";
        hit.setSnippet(prefix + snippet + suffix);
        return hit;
    }

    private LocalDateTime toLocalDateTime(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof LocalDateTime ldt) {
            return ldt;
        }
        if (v instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime();
        }
        return LocalDateTime.parse(v.toString().replace(' ', 'T'));
    }

    @Override
    public List<MessageVO> getChatHistoryMessage(String type, String role, String chatId, Long userId) {
        List<MessageVO> chatMessageHistory = chatMemoryRepository.getChatMessageHistory(type, role, chatId);
        // 会话不存在/已删除：透传 null，由控制器转 404（区别于"存在但无消息"的空列表）
        if (chatMessageHistory == null) {
            return null;
        }
        // 收藏星标合并进消息体（用户/助手消息均可收藏），前端星标回显不再单独请求 favorite/status
        fillFavorited(chatMessageHistory, userId);
        // 查出来
        List<String> assistantNos = chatMessageHistory.stream()
                .filter(messageVO -> messageVO.getRole().equals("assistant"))
                .map(MessageVO::getId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(assistantNos)) {
            return chatMessageHistory;
        }
        Map<String, String> chatModelMap = chatPersistenceService.selectChatModelByIds(assistantNos);
        chatMessageHistory.stream().filter(messageVO -> messageVO.getRole().equals("assistant")).forEach(messageVO -> {
            messageVO.setChatModel(chatModelMap.get(messageVO.getId()));
        });
        return chatMessageHistory;
    }

    /**
     * 合并收藏状态：批量查询当前用户已收藏的 messageNo，填充 MessageVO.favorited。
     * 分批查询避免 IN 列表过长（沿用收藏接口的批量上限）；userId 为空时不查库，favorited 保持 false。
     */
    private void fillFavorited(List<MessageVO> messages, Long userId) {
        if (userId == null || CollectionUtils.isEmpty(messages)) {
            return;
        }
        List<String> messageNos = messages.stream()
                .map(MessageVO::getId)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        if (messageNos.isEmpty()) {
            return;
        }
        int batchSize = ChatFavoriteServiceImpl.MAX_STATUS_BATCH;
        Set<String> favoritedNos = new HashSet<>();
        for (int i = 0; i < messageNos.size(); i += batchSize) {
            favoritedNos.addAll(chatFavoriteService.getFavoritedMessageNos(userId,
                    messageNos.subList(i, Math.min(i + batchSize, messageNos.size()))));
        }
        messages.forEach(messageVO ->
                messageVO.setFavorited(messageVO.getId() != null && favoritedNos.contains(messageVO.getId())));
    }

    @Override
    public Map<String, Object> getChatHistoryInfo(String type, String role) {
        Map<String, Object> result = new LinkedHashMap<>();
        Role roleConfig = myRolesConfig.getAllRoles().stream()
                .filter(r -> role != null && role.equals(r.getName())).findFirst().orElse(null);
        result.put("role", role == null ? "" : role);
        result.put("description", roleConfig != null && StringUtils.hasText(roleConfig.getDescription()) ? roleConfig.getDescription() : "");

        List<Map<String, Object>> rows = aiChatSessionMapper.statByRange(type, role);
        List<Map<String, Object>> ranges = new ArrayList<>();
        Map<String, Object> all = null;
        for (Map<String, Object> row : rows) {
            String label = String.valueOf(row.get("label"));
            Map<String, Object> range = new LinkedHashMap<>();
            range.put("label", label);
            range.put("sessionCount", toInt(row.get("session_count")));
            range.put("messageCount", toInt(row.get("message_count")));
            range.put("firstChatAt", formatTime(row.get("first_chat_at")));
            range.put("lastChatAt", formatTime(row.get("last_chat_at")));
            ranges.add(range);
            if ("全部".equals(label)) {
                all = row;
            }
        }
        result.put("ranges", ranges);

        if (all == null) {
            result.put("totalSessionCount", 0);
            result.put("totalMessageCount", 0);
            result.put("firstChatAt", "");
            result.put("lastChatAt", "");
            return result;
        }
        result.put("totalSessionCount", toInt(all.get("session_count")));
        result.put("totalMessageCount", toInt(all.get("message_count")));
        result.put("firstChatAt", formatTime(all.get("first_chat_at")));
        result.put("lastChatAt", formatTime(all.get("last_chat_at")));
        return result;
    }

    private int toInt(Object v) {
        return v == null ? 0 : ((Number) v).intValue();
    }

    private String formatTime(Object v) {
        if (v == null) {
            return "";
        }
        if (v instanceof LocalDateTime) {
            return ((LocalDateTime) v).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        return v.toString();
    }
}
