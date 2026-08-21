package com.qingsong.ai.service.impl.chat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingsong.ai.config.MyRolesConfig;
import com.qingsong.ai.entity.po.chat.ChatReviewRecord;
import com.qingsong.ai.entity.po.role.Role;
import com.qingsong.ai.entity.vo.chat.ChatReviewVO;
import com.qingsong.ai.entity.vo.roles.RoleStatsVO;
import com.qingsong.ai.entity.vo.roles.RoleUsageItem;
import com.qingsong.ai.mapper.chat.AiChatMessageMapper;
import com.qingsong.ai.mapper.chat.ChatReviewRecordMapper;
import com.qingsong.ai.service.ChatReviewService;
import com.qingsong.ai.service.RoleUsageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 对话复盘实现：基于 ai_chat_message / ai_chat_session 按日期聚合，并持久化解读结果。
 */
@Service
@RequiredArgsConstructor
public class ChatReviewServiceImpl implements ChatReviewService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 活跃时段分桶：{起始小时, 结束小时} */
    private static final int[][] TIME_BUCKETS = {
            {0, 5},   // 凌晨
            {6, 8},   // 早晨
            {9, 11},  // 上午
            {12, 13}, // 中午
            {14, 17}, // 下午
            {18, 21}, // 晚上
            {22, 23}, // 深夜
    };
    private static final String[] TIME_BUCKET_LABELS = {"凌晨", "早晨", "上午", "中午", "下午", "晚上", "深夜"};

    private final AiChatMessageMapper messageMapper;
    private final MyRolesConfig myRolesConfig;
    private final RoleUsageService roleUsageService;
    private final ChatReviewRecordMapper reviewRecordMapper;

    @Override
    public ChatReviewVO reviewDaily() {
        return reviewByDate(LocalDate.now());
    }

    @Override
    public ChatReviewVO reviewByDate(LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime endExclusive = date.plusDays(1).atStartOfDay();

        List<Map<String, Object>> roleRows = messageMapper.selectRoleStats(start, endExclusive);
        List<Map<String, Object>> hourRows = messageMapper.selectHourStats(start, endExclusive);

        Map<String, Role> roleMap = myRolesConfig.getRoleMap();

        long totalMessages = 0L, userMessages = 0L, totalRounds = 0L;
        List<ChatReviewVO.RoleReviewItem> roles = new ArrayList<>(roleRows.size());
        for (Map<String, Object> row : roleRows) {
            String roleCode = str(row.get("role_code"));
            long messages = num(row.get("messages"));
            long userMsg = num(row.get("user_messages"));
            long rounds = num(row.get("rounds"));
            totalMessages += messages;
            userMessages += userMsg;
            totalRounds += rounds;

            Role role = roleCode == null ? null : roleMap.get(roleCode);
            roles.add(ChatReviewVO.RoleReviewItem.builder()
                    .id(role == null ? null : role.getId())
                    .name(roleCode)
                    .messages(messages)
                    .userMessages(userMsg)
                    .sessions(rounds)
                    .build());
        }

        long activeRoles = roles.size();
        double avgRoundsPerRole = activeRoles == 0L
                ? 0D
                : Math.round(userMessages * 10.0 / activeRoles) / 10.0;

        RoleStatsVO stats = roleUsageService.getStats();
        ChatReviewVO.RoleLeaderboard leaderboard = ChatReviewVO.RoleLeaderboard.builder()
                .today(buildTodayLeaderboard(date, roleRows, roleMap, stats))
                .total(stats.getTotal())
                .build();

        return ChatReviewVO.builder()
                .date(date.format(DATE_FMT))
                .totalMessages(totalMessages)
                .userMessages(userMessages)
                .totalRounds(totalRounds)
                .activeRoles(activeRoles)
                .avgRoundsPerRole(avgRoundsPerRole)
                .roles(roles)
                .timeBuckets(buildTimeBuckets(hourRows))
                .leaderboard(leaderboard)
                .build();
    }

    /**
     * 当日榜：当天直接用 Redis 今日榜；历史日期用该天聚合的角色统计生成
     * （Redis 今日榜按 yyyyMMdd 建 key 且当日过期，无法还原历史）。
     */
    private List<RoleUsageItem> buildTodayLeaderboard(LocalDate date,
                                                      List<Map<String, Object>> roleRows,
                                                      Map<String, Role> roleMap,
                                                      RoleStatsVO stats) {
        if (date.equals(LocalDate.now())) {
            return stats.getToday();
        }
        List<RoleUsageItem> items = new ArrayList<>(roleRows.size());
        for (Map<String, Object> row : roleRows) {
            String roleCode = str(row.get("role_code"));
            Role role = roleCode == null ? null : roleMap.get(roleCode);
            items.add(RoleUsageItem.builder()
                    .id(role == null ? roleCode : role.getId())
                    .name(role == null ? roleCode : role.getName())
                    .count(num(row.get("user_messages")))
                    .build());
        }
        items.sort(Comparator.comparing(RoleUsageItem::getCount).reversed());
        return items;
    }

    @Override
    public void saveInsight(ChatReviewRecord record) {
        if (record == null || record.getReviewDate() == null) {
            return;
        }
        ChatReviewRecord existing = getInsight(record.getReviewDate());
        if (existing == null) {
            reviewRecordMapper.insert(record);
        } else {
            record.setId(existing.getId());
            reviewRecordMapper.updateById(record);
        }
    }

    @Override
    public ChatReviewRecord getInsight(LocalDate date) {
        if (date == null) {
            return null;
        }
        return reviewRecordMapper.selectOne(
                new LambdaQueryWrapper<ChatReviewRecord>()
                        .eq(ChatReviewRecord::getReviewDate, date)
                        .last("LIMIT 1"));
    }

    @Override
    public List<String> listInsightDates() {
        return reviewRecordMapper.selectList(
                        new LambdaQueryWrapper<ChatReviewRecord>()
                                .eq(ChatReviewRecord::getStatus, "DONE")
                                .orderByDesc(ChatReviewRecord::getReviewDate))
                .stream()
                .map(r -> r.getReviewDate().format(DATE_FMT))
                .toList();
    }

    private List<ChatReviewVO.TimeBucketItem> buildTimeBuckets(List<Map<String, Object>> hourRows) {
        Map<Integer, Long> hourMap = new HashMap<>();
        for (Map<String, Object> row : hourRows) {
            Object hour = row.get("hour");
            if (hour != null) {
                hourMap.put(((Number) hour).intValue(), num(row.get("messages")));
            }
        }

        List<ChatReviewVO.TimeBucketItem> list = new ArrayList<>(TIME_BUCKETS.length);
        for (int i = 0; i < TIME_BUCKETS.length; i++) {
            long sum = 0L;
            for (int h = TIME_BUCKETS[i][0]; h <= TIME_BUCKETS[i][1]; h++) {
                sum += hourMap.getOrDefault(h, 0L);
            }
            list.add(ChatReviewVO.TimeBucketItem.builder()
                    .label(TIME_BUCKET_LABELS[i])
                    .startHour(TIME_BUCKETS[i][0])
                    .endHour(TIME_BUCKETS[i][1])
                    .messages(sum)
                    .build());
        }
        return list;
    }

    private long num(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
