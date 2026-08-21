package com.qingsong.ai.entity.vo.roles;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 角色使用统计（总榜 + 今日榜）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleStatsVO {
    /** 总榜，按累计使用次数降序 */
    private List<RoleUsageItem> total = new ArrayList<>();
    /** 今日榜，按今日使用次数降序 */
    private List<RoleUsageItem> today = new ArrayList<>();
    /** 最近对话角色（id + name），无则 null */
    private RoleUsageItem lastRole;
}
