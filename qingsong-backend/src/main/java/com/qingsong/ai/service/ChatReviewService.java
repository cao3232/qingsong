package com.qingsong.ai.service;

import com.qingsong.ai.entity.po.chat.ChatReviewRecord;
import com.qingsong.ai.entity.vo.chat.ChatReviewVO;

import java.time.LocalDate;
import java.util.List;

/**
 * 对话复盘服务（日报）：按天聚合 + 解读结果持久化。
 */
public interface ChatReviewService {

    /**
     * 当日对话复盘：角色/会话/消息聚合 + 活跃时段 + 角色使用榜单。
     */
    ChatReviewVO reviewDaily();

    /**
     * 指定日期对话复盘：角色/会话/消息聚合 + 活跃时段 + 角色使用榜单。
     * <p>历史日期的「当日榜」用该天聚合的角色统计生成（Redis 今日榜当日过期，无法还原）；总榜仍用 Redis 累计榜。</p>
     */
    ChatReviewVO reviewByDate(LocalDate date);

    /**
     * 保存（upsert by review_date）解读记录。
     */
    void saveInsight(ChatReviewRecord record);

    /**
     * 读取指定日期已持久化的解读记录（无则返回 null）。
     */
    ChatReviewRecord getInsight(LocalDate date);

    /**
     * 已有解读记录的日期列表（yyyy-MM-dd，降序）。
     */
    List<String> listInsightDates();
}
