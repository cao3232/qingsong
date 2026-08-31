package com.qingsong.ai.service.chat;

import com.qingsong.ai.entity.vo.chat.ChatFavoritePageVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 聊天消息收藏（快照语义）：收藏时刻把消息内容复制进收藏表，
 * 原消息/会话删除后收藏内容依然完整，仅"查看原文"跳转能力随 sessionAlive 降级。
 */
public interface ChatFavoriteService {

    /**
     * 收藏消息（幂等）：从 ai_chat_message JOIN ai_chat_session 拷贝快照。
     *
     * @return true 新收藏；false 已收藏过（幂等命中）
     * @throws IllegalArgumentException 消息不存在或已删除
     */
    boolean favorite(Long userId, String messageNo);

    /**
     * 取消收藏。
     *
     * @return true 删除了记录；false 该消息本就不在收藏中（幂等）
     */
    boolean unfavorite(Long userId, String messageNo);

    /**
     * 收藏列表游标分页。keyword 匹配内容快照与会话标题（LIKE，方法内转义）；
     * before/beforeId 为上一页末条游标（首页传 null）。
     */
    ChatFavoritePageVO getFavoritePage(Long userId, String keyword, String roleCode,
                                       LocalDateTime before, Long beforeId, int limit);

    /**
     * 批量查询已收藏的 messageNo（聊天页星标回显）。
     */
    List<String> getFavoritedMessageNos(Long userId, List<String> messageNos);
}
