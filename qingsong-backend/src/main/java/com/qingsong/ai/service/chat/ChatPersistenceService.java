package com.qingsong.ai.service.chat;

import com.qingsong.ai.entity.po.ChatHistory;
import com.qingsong.ai.entity.vo.MessageVO;
import org.springframework.ai.chat.messages.Message;

import java.util.List;
import java.util.Map;

public interface ChatPersistenceService {

    void ensureSession(String bizType, String roleCode, String sessionNo, String title);

    void appendUserMessage(String bizType, String roleCode, String sessionNo, String content, String messageNo);

    void appendAssistantMessage(String bizType, String roleCode, String sessionNo, String content, String signalType, String chatModel);

    List<ChatHistory> getChatHistories(String bizType, String roleCode);

    List<MessageVO> getChatMessages(String bizType, String roleCode, String sessionNo);

    List<Message> getAllMessages(String sessionNo);

    List<Message> getRecentMessages(String sessionNo, int limit);

    boolean deleteSession(String bizType, String roleCode, String sessionNo);

    void updateSessionTitle(String bizType, String roleCode, String sessionNo, String title);

    Map<String, String> selectChatModelByIds(List<String> messageNos);

    void deleteLastRound(String sessionNo);

    void validateRetry(String sessionNo, String messageNo);

    void retryLastRound(String bizType, String roleCode, String sessionNo, String messageNo, String content);

    /**
     * 标记会话已转人工（客服场景）：status 置为 {@code escalated}。
     * 会话不存在或已删除时静默忽略（仅记日志），不影响工具调用链路。
     *
     * @param sessionNo 会话号
     * @param reason    转人工原因
     */
    void markEscalated(String sessionNo, String reason);
}
