package com.qingsong.ai.entity.vo.knowledge;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档实体类
 *
 * @author AI Architect
 * @since 2026-03-20
 */
@Data
public class KnowledgeBaseReqVO {

    /**
     * 是否激活：1-激活，0-禁用
     */
    private Boolean active;


}
