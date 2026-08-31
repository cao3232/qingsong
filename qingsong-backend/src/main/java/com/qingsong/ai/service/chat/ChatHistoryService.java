package com.qingsong.ai.service.chat;

import com.qingsong.ai.entity.vo.MessageVO;
import com.qingsong.ai.entity.vo.chat.ChatHistoryPageVO;
import com.qingsong.ai.entity.vo.chat.ChatSearchHitVO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * description...
 *
 * @author : caojiangjiang
 * @data : 2026/04/28 10:28
 */
public interface ChatHistoryService {

    /**
     * 查询会话消息明细。
     * 返回 null 表示会话不存在或已删除（区别于"存在但无消息"的空列表），供控制器转 404。
     * userId 用于填充每条消息的 favorited 收藏星标（为 null 时全部置 false）。
     */
    List<MessageVO> getChatHistoryMessage(String type, String role, String chatId, Long userId);

    Map<String, Object> getChatHistoryInfo(String type, String role);

    /**
     * 游标分页查询会话列表。keyword 匹配会话标题（LIKE，方法内转义）；
     * start/end 为 COALESCE(lastMessageAt, createdAt) 的左闭右开区间；
     * before/beforeId 为上一页末条游标（首页传 null）。
     */
    ChatHistoryPageVO getChatHistoryPage(String type, String role, String keyword,
                                         LocalDateTime start, LocalDateTime end,
                                         LocalDateTime before, Long beforeId, int limit);

    /**
     * 有会话记录的日期集合（yyyy-MM-dd 降序），供前端日历高亮有记录日期。
     */
    List<String> getChatHistoryDates(String type, String role);

    /**
     * 消息内容搜索：返回消息粒度命中项（snippet 为关键词上下文片段，按需要带省略号）。
     * 关键词为空白时直接返回空列表，不查库。
     */
    List<ChatSearchHitVO> searchChatMessages(String type, String role, String keyword,
                                             LocalDateTime start, LocalDateTime end, int limit);
}
