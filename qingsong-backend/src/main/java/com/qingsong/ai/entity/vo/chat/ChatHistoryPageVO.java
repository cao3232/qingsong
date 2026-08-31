package com.qingsong.ai.entity.vo.chat;

import com.qingsong.ai.entity.po.ChatHistory;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 会话列表游标分页结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatHistoryPageVO {
    private List<ChatHistory> list;
    private boolean hasMore;
}
