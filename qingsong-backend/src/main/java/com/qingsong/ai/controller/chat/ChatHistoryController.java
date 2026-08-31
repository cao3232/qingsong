package com.qingsong.ai.controller.chat;

import cn.dev33.satoken.stp.StpUtil;
import com.qingsong.ai.entity.po.ChatHistory;
import com.qingsong.ai.entity.vo.MessageVO;
import com.qingsong.ai.entity.vo.Result;
import com.qingsong.ai.entity.vo.chat.ChatHistoryPageVO;
import com.qingsong.ai.entity.vo.chat.ChatSearchHitVO;
import com.qingsong.ai.entity.vo.chat.RedisMigrationSummary;
import com.qingsong.ai.repository.ChatHistoryRepository;
import com.qingsong.ai.repository.ChatMemoryRepository;
import com.qingsong.ai.service.chat.ChatHistoryService;
import com.qingsong.ai.service.chat.LegacyRedisChatMigrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
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
     * 游标分页查询会话列表：支持标题关键词与时间范围过滤。
     * 游标为上一页末条的 (COALESCE(lastMessageAt, createdAt), sessionDbId)，首页不传 before/beforeId。
     */
    @GetMapping("/{type}/{role}/page")
    public Result<ChatHistoryPageVO> getChatHistoryPage(
            @PathVariable("type") String type, @PathVariable("role") String role,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "start", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(value = "end", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(value = "before", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime before,
            @RequestParam(value = "beforeId", required = false) Long beforeId,
            @RequestParam(value = "limit", defaultValue = "15") int limit) {
        return Result.ok(chatHistoryService.getChatHistoryPage(type, role, keyword, start, end, before, beforeId, limit));
    }

    /**
     * 有会话记录的日期集合（yyyy-MM-dd 降序），供前端日历高亮有记录日期。
     */
    @GetMapping("/{type}/{role}/dates")
    public Result<List<String>> getChatHistoryDates(@PathVariable("type") String type, @PathVariable("role") String role) {
        return Result.ok(chatHistoryService.getChatHistoryDates(type, role));
    }

    /**
     * 消息内容搜索：返回消息粒度命中项（含关键词上下文摘要），按时间倒序。
     */
    @GetMapping("/{type}/{role}/search")
    public Result<List<ChatSearchHitVO>> searchChatMessages(
            @PathVariable("type") String type, @PathVariable("role") String role,
            @RequestParam("keyword") String keyword,
            @RequestParam(value = "start", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(value = "end", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(value = "limit", defaultValue = "100") int limit) {
        return Result.ok(chatHistoryService.searchChatMessages(type, role, keyword, start, end, limit));
    }

    /**
     * 查询指定会话的消息明细。
     * 消息体中的 createdAt / id 由持久化层统一组装，前端不再依赖 chatId 推导时间。
     * 每条消息合并 favorited 收藏星标（按当前登录用户），前端不再单独请求 favorite/status。
     * 会话不存在或已删除时返回 404 + Result.fail（区别于"存在但无消息"的 200 空列表）。
     */
    @GetMapping("/{type}/{role}/{chatId}")
    public ResponseEntity<?> getChatHistoryMsg(@PathVariable("type") String type, @PathVariable("role") String role, @PathVariable("chatId") String chatId) {
        List<MessageVO> messages = chatHistoryService.getChatHistoryMessage(type, role, chatId, StpUtil.getLoginIdAsLong());
        if (messages == null) {
            return ResponseEntity.status(404).body(Result.fail("会话不存在或已删除"));
        }
        return ResponseEntity.ok(messages);
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
