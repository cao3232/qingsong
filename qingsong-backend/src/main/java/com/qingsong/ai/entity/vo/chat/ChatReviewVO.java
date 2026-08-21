package com.qingsong.ai.entity.vo.chat;

import com.qingsong.ai.entity.vo.roles.RoleUsageItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 对话复盘（日报）：当日角色/会话/消息聚合 + 活跃时段 + 榜单。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatReviewVO {

    /** 统计日期（yyyy-MM-dd） */
    private String date;

    /** 消息总数（用户 + 助手） */
    private Long totalMessages;

    /** 用户消息数（对话轮次） */
    private Long userMessages;

    /** 会话数 */
    private Long totalRounds;

    /** 活跃角色数 */
    private Long activeRoles;

    /** 平均每角色轮次 = userMessages / activeRoles */
    private Double avgRoundsPerRole;

    /** 按角色统计，按消息数降序 */
    private List<RoleReviewItem> roles = new ArrayList<>();

    /** 活跃时段分布 */
    private List<TimeBucketItem> timeBuckets = new ArrayList<>();

    /** 角色使用榜单（Redis：今日榜 + 总榜） */
    private RoleLeaderboard leaderboard;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoleReviewItem {
        private String id;
        private String name;
        private Long messages;
        private Long userMessages;
        private Long sessions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeBucketItem {
        private String label;
        private Integer startHour;
        private Integer endHour;
        private Long messages;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoleLeaderboard {
        private List<RoleUsageItem> today = new ArrayList<>();
        private List<RoleUsageItem> total = new ArrayList<>();
    }
}
