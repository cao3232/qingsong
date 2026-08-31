package com.qingsong.ai.service.impl.chat;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.qingsong.ai.entity.po.chat.AiChatFavorite;
import com.qingsong.ai.entity.po.chat.AiChatMessage;
import com.qingsong.ai.entity.po.chat.AiChatSession;
import com.qingsong.ai.entity.vo.chat.ChatFavoriteItemVO;
import com.qingsong.ai.entity.vo.chat.ChatFavoritePageVO;
import com.qingsong.ai.mapper.chat.AiChatFavoriteMapper;
import com.qingsong.ai.mapper.chat.AiChatMessageMapper;
import com.qingsong.ai.mapper.chat.AiChatSessionMapper;
import com.qingsong.ai.service.chat.ChatFavoriteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 聊天消息收藏实现：收藏时刻从 ai_chat_message JOIN ai_chat_session 拷贝快照，
 * 取消收藏物理删除（唯一键 uk_user_message 兜底并发重复收藏）。
 */
@Service
public class ChatFavoriteServiceImpl implements ChatFavoriteService {

    static final int DEFAULT_PAGE_SIZE = 15;
    static final int MAX_PAGE_SIZE = 100;
    static final int MAX_KEYWORD_LENGTH = 50;
    static final int MAX_STATUS_BATCH = 200;

    @Autowired
    private AiChatFavoriteMapper aiChatFavoriteMapper;
    @Autowired
    private AiChatMessageMapper aiChatMessageMapper;
    @Autowired
    private AiChatSessionMapper aiChatSessionMapper;

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
    public boolean favorite(Long userId, String messageNo) {
        if (userId == null || !StringUtils.hasText(messageNo)) {
            throw new IllegalArgumentException("messageNo 不能为空");
        }

        // 幂等：已收藏直接返回 false，不再拷贝快照
        Long exists = aiChatFavoriteMapper.selectCount(new QueryWrapper<AiChatFavorite>()
                .eq("user_id", userId)
                .eq("message_no", messageNo));
        if (exists != null && exists > 0) {
            return false;
        }

        AiChatMessage message = aiChatMessageMapper.selectOne(new QueryWrapper<AiChatMessage>()
                .eq("message_no", messageNo)
                .eq("deleted", 0)
                .last("LIMIT 1"));
        if (message == null) {
            throw new IllegalArgumentException("消息不存在或已删除");
        }

        AiChatSession session = aiChatSessionMapper.selectById(message.getSessionId());

        AiChatFavorite favorite = new AiChatFavorite();
        favorite.setUserId(userId);
        favorite.setMessageNo(message.getMessageNo());
        favorite.setSessionNo(session == null ? "" : Objects.toString(session.getSessionNo(), ""));
        favorite.setRoleCode(session == null ? "" : Objects.toString(session.getRoleCode(), ""));
        favorite.setMessageType(Objects.toString(message.getMessageType(), ""));
        favorite.setContent(Objects.toString(message.getContent(), ""));
        favorite.setContentFormat(Objects.toString(message.getContentFormat(), "TEXT"));
        favorite.setSessionTitle(session == null ? "" : Objects.toString(session.getTitle(), ""));
        favorite.setChatModel(message.getChatModel());
        favorite.setMessageCreatedAt(message.getCreatedAt());
        aiChatFavoriteMapper.insert(favorite);
        return true;
    }

    @Override
    public boolean unfavorite(Long userId, String messageNo) {
        if (userId == null || !StringUtils.hasText(messageNo)) {
            throw new IllegalArgumentException("messageNo 不能为空");
        }
        int deleted = aiChatFavoriteMapper.delete(new QueryWrapper<AiChatFavorite>()
                .eq("user_id", userId)
                .eq("message_no", messageNo));
        return deleted > 0;
    }

    @Override
    public ChatFavoritePageVO getFavoritePage(Long userId, String keyword, String roleCode,
                                              LocalDateTime before, Long beforeId, int limit) {
        int pageSize = clampPageSize(limit);
        // 多取 1 条用于判断 hasMore
        List<Map<String, Object>> rows = aiChatFavoriteMapper.selectPageByCursor(
                userId, escapeLike(keyword),
                StringUtils.hasText(roleCode) ? roleCode.trim() : null,
                before, beforeId, pageSize + 1);
        boolean hasMore = rows.size() > pageSize;
        if (hasMore) {
            rows = new ArrayList<>(rows.subList(0, pageSize));
        }
        List<ChatFavoriteItemVO> list = rows.stream().map(this::rowToItem).toList();
        return new ChatFavoritePageVO(list, hasMore);
    }

    @Override
    public List<String> getFavoritedMessageNos(Long userId, List<String> messageNos) {
        if (userId == null || CollectionUtils.isEmpty(messageNos)) {
            return List.of();
        }
        List<String> cleaned = messageNos.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .limit(MAX_STATUS_BATCH)
                .toList();
        if (cleaned.isEmpty()) {
            return List.of();
        }
        return aiChatFavoriteMapper.selectFavoritedMessageNos(userId, cleaned);
    }

    private ChatFavoriteItemVO rowToItem(Map<String, Object> row) {
        ChatFavoriteItemVO item = new ChatFavoriteItemVO();
        Object favoriteId = row.get("favoriteId");
        item.setFavoriteId(favoriteId == null ? null : ((Number) favoriteId).longValue());
        item.setMessageNo(Objects.toString(row.get("messageNo"), null));
        item.setSessionNo(Objects.toString(row.get("sessionNo"), ""));
        item.setRoleCode(Objects.toString(row.get("roleCode"), ""));
        item.setMessageType(Objects.toString(row.get("messageType"), ""));
        item.setContent(Objects.toString(row.get("content"), ""));
        item.setSessionTitle(Objects.toString(row.get("sessionTitle"), ""));
        item.setChatModel(Objects.toString(row.get("chatModel"), null));
        item.setMessageCreatedAt(toLocalDateTime(row.get("messageCreatedAt")));
        item.setCreatedAt(toLocalDateTime(row.get("createdAt")));
        Object alive = row.get("sessionAlive");
        // MySQL 表达式经 JDBC 返回 Boolean/Number 皆有可能，统一按"非 0"判定
        item.setSessionAlive(alive instanceof Boolean b ? b : alive != null && ((Number) alive).longValue() != 0);
        Object msgAlive = row.get("messageAlive");
        item.setMessageAlive(msgAlive instanceof Boolean b2 ? b2 : msgAlive != null && ((Number) msgAlive).longValue() != 0);
        return item;
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
}
