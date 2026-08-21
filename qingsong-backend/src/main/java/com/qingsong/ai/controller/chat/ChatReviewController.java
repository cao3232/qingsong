package com.qingsong.ai.controller.chat;

import com.qingsong.ai.entity.po.chat.ChatReviewRecord;
import com.qingsong.ai.entity.vo.Result;
import com.qingsong.ai.entity.vo.chat.ChatReviewVO;
import com.qingsong.ai.service.ChatReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 对话复盘（日报）：按天聚合 + 解读结果持久化。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/ai/stats")
public class ChatReviewController {

    private final ChatReviewService chatReviewService;

    /**
     * 指定日期对话复盘：角色/会话/消息聚合 + 活跃时段 + 角色使用榜单。
     *
     * @param date 统计日期（yyyy-MM-dd，必传）
     */
    @GetMapping("/review")
    public Result<ChatReviewVO> review(
            @RequestParam(required = true)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.ok(chatReviewService.reviewByDate(date));
    }

    /**
     * 指定日期已持久化的 AI 解读记录（无则返回 null）。
     *
     * @param date 统计日期（yyyy-MM-dd，必传）
     */
    @GetMapping("/review/insight")
    public Result<ChatReviewRecord> insight(
            @RequestParam(required = true)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.ok(chatReviewService.getInsight(date));
    }

    /**
     * 已有解读记录的日期列表（yyyy-MM-dd，降序）。
     */
    @GetMapping("/review/dates")
    public Result<List<String>> dates() {
        return Result.ok(chatReviewService.listInsightDates());
    }
}
