package com.qingsong.ai.controller.chat;

import com.qingsong.ai.entity.po.ChatHistory;
import com.qingsong.ai.entity.vo.MessageVO;
import com.qingsong.ai.entity.vo.Result;
import com.qingsong.ai.entity.vo.chat.RedisMigrationSummary;
import com.qingsong.ai.repository.ChatHistoryRepository;
import com.qingsong.ai.repository.ChatMemoryRepository;
import com.qingsong.ai.service.chat.ChatHistoryService;
import com.qingsong.ai.service.chat.LegacyRedisChatMigrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@RestController
@RequestMapping("/ai/history")
public class ChatHistoryController {

    private final ChatHistoryRepository chatHistoryRepository;
    private final ChatMemoryRepository chatMemoryRepository;
    private final LegacyRedisChatMigrationService legacyRedisChatMigrationService;
    private final ChatHistoryService chatHistoryService;

    @Value("${chat.history.redis-migration.enabled:false}")
    private boolean redisMigrationEnabled;

    /**
     * 查询指定业务类型 + 角色下的会话列表。
     * 返回值同时承载前端展示字段和 Redis session meta 缓存字段。
     */
    @GetMapping("/{type}/{role}")
    public List<ChatHistory> getChatHistorys(@PathVariable("type") String type, @PathVariable("role") String role) {
        return chatHistoryRepository.getChatHistorys(type, role);
    }

    /**
     * 查询指定会话的消息明细。
     * 消息体中的 createdAt / id 由持久化层统一组装，前端不再依赖 chatId 推导时间。
     */
    @GetMapping("/{type}/{role}/{chatId}")
    public List<MessageVO> getChatHistoryMsg(@PathVariable("type") String type, @PathVariable("role") String role, @PathVariable("chatId") String chatId) {
        return chatHistoryService.getChatHistoryMessage(type, role, chatId);
    }


    /**
     * 逻辑删除会话，并清理对应缓存。
     */
    @DeleteMapping("/{type}/{role}/{chatId}")
    public Boolean deleteChatHistory(@PathVariable("type") String type, @PathVariable("role") String role, @PathVariable("chatId") String chatId) {
        return chatHistoryRepository.deleteChatHistory(type, role, chatId);
    }


    /**
     * 更新会话标题，并同步刷新 session meta 缓存。
     */
    @PutMapping("/{type}/{role}/{chatId}/{chatHistoryName}")
    public Result<Boolean> updateChatHistoryName(@PathVariable("type") String type, @PathVariable("role") String role, @PathVariable("chatId") String chatId, @PathVariable("chatHistoryName") String chatHistoryName) {
        chatHistoryRepository.updateChatHistoryName(type, role, chatId, chatHistoryName);
        return Result.ok(Boolean.TRUE);
    }


    @GetMapping("/{type}/{role}/info")
    public Map<String,Object> getChatHistoryInfo(@PathVariable("type") String type, @PathVariable("role") String role) {
        return chatHistoryService.getChatHistoryInfo(type, role);
    }


    @PostMapping("/migrate/redis")
    public Result<RedisMigrationSummary> migrateLegacyRedisHistory() {
        if (!redisMigrationEnabled) {
            return Result.fail("Redis历史迁移开关未开启");
        }
        return Result.ok(legacyRedisChatMigrationService.migrateAll());
    }
}
