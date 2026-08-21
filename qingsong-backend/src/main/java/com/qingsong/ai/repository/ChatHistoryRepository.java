package com.qingsong.ai.repository;

import com.qingsong.ai.entity.po.ChatHistory;

import java.util.List;

public interface ChatHistoryRepository {

    /**
     * 保存会话记录
     *
     * @param type   业务类型，如：service、pdf
     * @param chatId 会话ID
     */
    void checkAndSave(String type, String chatId);

    /**
     * 保存会话记录
     *
     * @param type   业务类型，如：chat
     * @param chatId 会话ID
     */
    void checkAndSave(String type, String chatId, String role);

    /**
     * 获取会话ID列表
     *
     * @param type 业务类型，如：chat、service、pdf
     * @return 会话ID列表
     */
    List<ChatHistory> getChatHistorys(String type, String role);

    Boolean deleteChatHistory(String type, String role, String chatId);

    void updateChatHistoryName(String type, String role, String chatId, String chatHistoryName);
}
