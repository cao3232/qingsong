package com.qingsong.ai.entity.po.knowledge;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库实体类
 *
 * @author AI Architect
 * @since 2026-03-20
 */
@Data
@TableName("knowledge_base")
public class KnowledgeBase {

    /**
     * 主键 ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 知识库名称
     */
    @TableField("name")
    private String name;

    /**
     * 知识库描述
     */
    @TableField("description")
    private String description;

    /**
     * 是否激活：1-激活，0-禁用
     */
    @TableField("is_active")
    private Boolean active;

    /**
     * 创建时间
     */
    @TableField(value = "create_date", fill = FieldFill.INSERT)
    private LocalDateTime createDate;

    /**
     * 更新时间
     */
    @TableField(value = "update_date", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateDate;
}
