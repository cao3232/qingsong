package com.qingsong.ai.mapper.knowledge;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qingsong.ai.entity.po.knowledge.DocumentBase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 文档 Mapper 接口
 *
 * @author AI Architect
 * @since 2026-03-20
 */
@Mapper
public interface DocumentMapper extends BaseMapper<DocumentBase> {

    /**
     * 根据知识库 ID 查询所有文档
     *
     * @param knowledgeId 知识库 ID
     * @return 文档列表
     */
    List<DocumentBase> selectByKnowledgeId(@Param("knowledgeId") Long knowledgeId);

    /**
     * 查询指定知识库下未嵌入向量的文档
     *
     * @param knowledgeId 知识库 ID
     * @return 文档列表
     */
    List<DocumentBase> selectNotEmbeddedDocuments(@Param("knowledgeId") Long knowledgeId);

    /**
     * 根据来源 ID 查询文档
     *
     * @param sourceId 来源 ID
     * @return 文档列表
     */
    List<DocumentBase> selectBySourceId(@Param("sourceId") String sourceId);

    /**
     * 统计知识库的文档总数
     *
     * @param knowledgeId 知识库 ID
     * @return 文档数量
     */
    Long countByKnowledgeId(@Param("knowledgeId") Long knowledgeId);

    /**
     * 批量更新文档的嵌入状态
     *
     * @param ids         文档 ID 列表
     * @param isEmbedding 是否已嵌入
     * @return 影响行数
     */
    int batchUpdateEmbeddingStatus(@Param("ids") List<Long> ids, @Param("isEmbedding") Boolean isEmbedding);

    /**
     * 根据文件名称模糊查询文档
     *
     * @param knowledgeId 知识库 ID
     * @param fileName    文件名关键词
     * @return 文档列表
     */
    List<DocumentBase> selectByFileNameLike(@Param("knowledgeId") Long knowledgeId, @Param("fileName") String fileName);

    /**
     * 删除指定知识库的所有文档
     *
     * @param knowledgeId 知识库 ID
     * @return 影响行数
     */
    int deleteByKnowledgeId(@Param("knowledgeId") Long knowledgeId);
}
