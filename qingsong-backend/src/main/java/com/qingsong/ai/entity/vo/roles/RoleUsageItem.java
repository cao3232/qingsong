package com.qingsong.ai.entity.vo.roles;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 角色使用榜单项。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleUsageItem {
    private String id;
    private String name;
    private Long count;
}
