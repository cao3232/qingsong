package com.qingsong.ai.service.chat;

import com.qingsong.ai.entity.vo.MessageVO;

import java.util.List;
import java.util.Map;

/**
 * description...
 *
 * @author : caojiangjiang
 * @data : 2026/04/28 10:28
 */
public interface ChatHistoryService {
    List<MessageVO> getChatHistoryMessage(String type, String role, String chatId);

    Map<String, Object> getChatHistoryInfo(String type, String role);
}
