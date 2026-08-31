package com.qingsong.ai.controller.chat;

import cn.dev33.satoken.stp.StpUtil;
import com.qingsong.ai.entity.vo.Result;
import com.qingsong.ai.entity.vo.chat.ChatFavoritePageVO;
import com.qingsong.ai.service.chat.ChatFavoriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 聊天消息收藏：收藏即快照（副本不随原消息删除），
 * 列表游标分页 + 批量状态查询（聊天页星标回显）。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/ai/chat/favorite")
public class ChatFavoriteController {

    private final ChatFavoriteService chatFavoriteService;

    /**
     * 收藏消息（幂等）：body {"messageNo": "..."}。
     * 已收藏过返回 ok + data=false，不重复拷贝快照。
     */
    @PostMapping
    public Result<Boolean> favorite(@RequestBody Map<String, String> body) {
        Long userId = StpUtil.getLoginIdAsLong();
        String messageNo = body == null ? null : body.get("messageNo");
        try {
            return Result.ok(chatFavoriteService.favorite(userId, messageNo));
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 取消收藏（幂等）。
     */
    @DeleteMapping("/{messageNo}")
    public Result<Boolean> unfavorite(@PathVariable("messageNo") String messageNo) {
        Long userId = StpUtil.getLoginIdAsLong();
        try {
            return Result.ok(chatFavoriteService.unfavorite(userId, messageNo));
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 收藏列表游标分页：keyword 匹配内容快照与会话标题；roleCode 过滤角色；
     * before/beforeId 为上一页末条游标（首页不传）。
     */
    @GetMapping("/page")
    public Result<ChatFavoritePageVO> page(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "roleCode", required = false) String roleCode,
            @RequestParam(value = "before", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime before,
            @RequestParam(value = "beforeId", required = false) Long beforeId,
            @RequestParam(value = "limit", defaultValue = "15") int limit) {
        Long userId = StpUtil.getLoginIdAsLong();
        return Result.ok(chatFavoriteService.getFavoritePage(userId, keyword, roleCode, before, beforeId, limit));
    }

    /**
     * 批量查询已收藏的 messageNo（聊天页星标回显），逗号分隔。
     */
    @GetMapping("/status")
    public Result<List<String>> status(@RequestParam("messageNos") String messageNos) {
        Long userId = StpUtil.getLoginIdAsLong();
        List<String> nos = messageNos == null ? List.of() : List.of(messageNos.split(","));
        return Result.ok(chatFavoriteService.getFavoritedMessageNos(userId, nos));
    }
}
