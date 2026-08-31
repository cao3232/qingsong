package com.qingsong.ai.entity.vo.knowledge;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档分页返回 VO
 *
 * @author caojiangjiang
 */
@Data
public class DocumentRespVO {

    /**
     * 主键 ID
     */
    private Long id;

    /**
     * 所属知识库 ID
     */
    private Long knowledgeId;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 文件存储路径
     */
    private String path;

    /**
     * 来源 ID（关联 origin_file_source.id）
     */
    private String sourceId;

    /**
     * 是否已嵌入向量：1-是，0-否
     */
    private Boolean embedding;

    /**
     * 创建时间
     */
    private LocalDateTime createDate;

    /**
     * 更新时间
     */
    private LocalDateTime updateDate;

    /**
     * 文件大小（字节）
     */
    private Long size;
}
