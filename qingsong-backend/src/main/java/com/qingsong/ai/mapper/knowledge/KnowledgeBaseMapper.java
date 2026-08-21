package com.qingsong.ai.mapper.knowledge;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qingsong.ai.entity.po.knowledge.KnowledgeBase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 知识库 Mapper 接口
 *
 * @author AI Architect
 * @since 2026-03-20
 */
@Mapper
public interface KnowledgeBaseMapper extends BaseMapper<KnowledgeBase> {

    /**
     * 查询所有激活的知识库
     *
     * @return 激活的知识库列表
     */
    List<KnowledgeBase> selectActiveKnowledgeBases();

    /**
     * 根据名称模糊查询知识库
     *
     * @param keyword 关键词
     * @return 知识库列表
     */
    List<KnowledgeBase> selectByKeyword(@Param("keyword") String keyword);

    /**
     * 统计每个知识库的文档数量
     *
     * @param knowledgeId 知识库 ID
     * @return 文档数量
     */
    Long countDocumentsByKnowledgeId(@Param("knowledgeId") Long knowledgeId);

    /**
     * 批量更新知识库状态
     *
     * @param ids      知识库 ID 列表
     * @param isActive 是否激活
     * @return 影响行数
     */
    int batchUpdateStatus(@Param("ids") List<Long> ids, @Param("isActive") Boolean isActive);

    /**
     * 逻辑删除知识库
     *
     * @param id 知识库 ID
     * @return 影响行数
     */
    int logicDelete(@Param("id") Long id);
}
