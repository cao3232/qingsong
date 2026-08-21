package com.qingsong.ai.service.impl.chat;

import com.qingsong.ai.config.MyRolesConfig;
import com.qingsong.ai.entity.po.role.Role;
import com.qingsong.ai.entity.vo.MessageVO;
import com.qingsong.ai.mapper.chat.AiChatSessionMapper;
import com.qingsong.ai.repository.ChatMemoryRepository;
import com.qingsong.ai.service.chat.ChatHistoryService;
import com.qingsong.ai.service.chat.ChatPersistenceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    @Override
    public List<MessageVO> getChatHistoryMessage(String type, String role, String chatId) {
        List<MessageVO> chatMessageHistory = chatMemoryRepository.getChatMessageHistory(type, role, chatId);
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
