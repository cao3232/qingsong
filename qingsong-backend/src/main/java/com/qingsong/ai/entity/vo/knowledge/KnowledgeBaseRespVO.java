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
public class KnowledgeBaseRespVO {


    /**
     * 主键 ID
     */
    private Long id;

    /**
     * 知识库名称
     */
    private String name;

    /**
     * 知识库描述
     */
    private String description;

    /**
     * 是否激活：1-激活，0-禁用
     */
    private Boolean active;

    /**
     * 创建时间
     */
    private LocalDateTime createDate;

    /**
     * 更新时间
     */
    private LocalDateTime updateDate;

    /**
     * 文档数量
     */
    private Long documentCount;

}
