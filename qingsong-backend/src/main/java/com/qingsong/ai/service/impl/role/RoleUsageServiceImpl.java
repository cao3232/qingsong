package com.qingsong.ai.service.impl.role;

import com.qingsong.ai.config.MyRolesConfig;
import com.qingsong.ai.constants.RedisConstants;
import com.qingsong.ai.entity.po.role.Role;
import com.qingsong.ai.entity.vo.roles.RoleStatsVO;
import com.qingsong.ai.entity.vo.roles.RoleUsageItem;
import com.qingsong.ai.service.RoleUsageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 角色使用统计实现：总榜 + 今日榜，均使用 Redis zset 存储。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleUsageServiceImpl implements RoleUsageService {

    private static final int TOP_N = 10;
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final StringRedisTemplate stringRedisTemplate;
    private final MyRolesConfig myRolesConfig;

    @Override
    public void recordUsage(String roleId) {
        if (!StringUtils.hasText(roleId)) {
            return;
        }
        try {
            stringRedisTemplate.opsForZSet().incrementScore(RedisConstants.ROLE_USAGE_TOTAL_KEY.getRedisKey(), roleId, 1.0);

            String todayKey = todayKey();
            stringRedisTemplate.opsForZSet().incrementScore(todayKey, roleId, 1.0);
            // 今日榜当日过期，次日自动重置
            stringRedisTemplate.expire(todayKey, Duration.between(LocalDateTime.now(), LocalDate.now().plusDays(1).atStartOfDay()));

            // 记录最近对话角色
            stringRedisTemplate.opsForValue().set(RedisConstants.ROLE_USAGE_LAST_KEY.getRedisKey(), roleId);
        } catch (Exception e) {
            // 统计失败不影响主流程，仅记录日志
            log.warn("记录角色使用次数失败, roleId={}", roleId, e);
        }
    }

    @Override
    public RoleStatsVO getStats() {
        Map<String, String> roleNameMap = myRolesConfig.getAllRoles().stream()
                .collect(Collectors.toMap(Role::getId, Role::getName, (a, b) -> a));

        List<RoleUsageItem> total = toItems(
                RedisConstants.ROLE_USAGE_TOTAL_KEY.getRedisKey(),
                roleNameMap);
        List<RoleUsageItem> today = toItems(todayKey(), roleNameMap);

        RoleUsageItem lastRole = toLastRole(roleNameMap);

        return RoleStatsVO.builder().total(total).today(today).lastRole(lastRole).build();
    }

    private RoleUsageItem toLastRole(Map<String, String> roleNameMap) {
        String roleId = stringRedisTemplate.opsForValue().get(RedisConstants.ROLE_USAGE_LAST_KEY.getRedisKey());
        if (!StringUtils.hasText(roleId)) {
            return null;
        }
        String name = roleNameMap.get(roleId);
        if (name == null) {
            // 已删除/不存在的角色忽略
            return null;
        }
        return RoleUsageItem.builder().id(roleId).name(name).build();
    }

    private List<RoleUsageItem> toItems(String key, Map<String, String> roleNameMap) {
        Set<ZSetOperations.TypedTuple<String>> tuples = stringRedisTemplate.opsForZSet()
                .reverseRangeWithScores(key, 0, TOP_N - 1);
        if (tuples == null || tuples.isEmpty()) {
            return new ArrayList<>();
        }
        List<RoleUsageItem> items = new ArrayList<>(tuples.size());
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            String roleId = tuple.getValue();
            String name = roleNameMap.get(roleId);
            if (roleId == null || name == null) {
                // 已删除/不存在的角色过滤掉
                continue;
            }
            Double score = tuple.getScore();
            items.add(RoleUsageItem.builder()
                    .id(roleId)
                    .name(name)
                    .count(score == null ? 0L : score.longValue())
                    .build());
        }
        return items;
    }

    private String todayKey() {
        return String.format(RedisConstants.ROLE_USAGE_TODAY_KEY.getRedisKey(), LocalDate.now().format(DAY_FORMATTER));
    }
}
