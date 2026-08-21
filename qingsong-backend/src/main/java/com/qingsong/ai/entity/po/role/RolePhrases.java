package com.qingsong.ai.entity.po.role;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RolePhrases {
    private Long id;
    private Long roleId;
    private String phrase;
}
