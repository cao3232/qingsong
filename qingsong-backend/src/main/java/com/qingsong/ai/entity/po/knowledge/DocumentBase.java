package com.qingsong.ai.entity.po.knowledge;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档实体类
 *
 * @author AI Architect
 * @since 2026-03-20
 */
@Data
@TableName("document")
public class DocumentBase {

    /**
     * 主键 ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属知识库 ID
     */
    @TableField("knowledge_id")
    private Long knowledgeId;

    /**
     * 文件名
     */
    @TableField("file_name")
    private String fileName;

    /**
     * 文件存储路径
     */
    @TableField("path")
    private String path;

    /**
     * 来源 ID（关联 origin_file_source.id）
     */
    @TableField("source_id")
    private String sourceId;

    /**
     * 是否已嵌入向量：1-是，0-否
     */
    @TableField("is_embedding")
    private Boolean embedding;

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
