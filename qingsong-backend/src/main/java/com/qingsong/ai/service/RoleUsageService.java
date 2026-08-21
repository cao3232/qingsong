package com.qingsong.ai.service;

import com.qingsong.ai.entity.vo.roles.RoleStatsVO;

/**
 * 角色使用统计服务（总榜 + 今日榜）。
 */
public interface RoleUsageService {

    /**
     * 记录一次角色使用（每次发起对话请求计 1 次）。
     *
     * @param roleId 角色ID
     */
    void recordUsage(String roleId);

    /**
     * 获取角色使用统计（总榜 + 今日榜，均按使用次数降序，各取前 10）。
     */
    RoleStatsVO getStats();
}
