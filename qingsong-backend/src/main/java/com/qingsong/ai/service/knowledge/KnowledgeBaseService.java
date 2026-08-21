package com.qingsong.ai.service.knowledge;

import com.baomidou.mybatisplus.extension.service.IService;
import com.qingsong.ai.entity.po.knowledge.KnowledgeBase;
import com.qingsong.ai.entity.vo.knowledge.KnowledgeBaseReqVO;
import com.qingsong.ai.entity.vo.knowledge.KnowledgeBaseRespVO;

import java.util.List;

/**
 * 知识库 Service 接口
 *
 * @author AI Architect
 * @since 2026-03-20
 */
public interface KnowledgeBaseService extends IService<KnowledgeBase> {

    /**
     * 查询所有激活的知识库
     *
     * @return 激活的知识库列表
     */
    List<KnowledgeBaseRespVO> getActiveKnowledgeBases(KnowledgeBaseReqVO reqVO);

    /**
     * 根据名称模糊查询知识库
     *
     * @param keyword 关键词
     * @return 知识库列表
     */
    List<KnowledgeBase> searchByKeyword(String keyword);

    /**
     * 统计每个知识库的文档数量
     *
     * @param knowledgeId 知识库 ID
     * @return 文档数量
     */
    Long countDocuments(Long knowledgeId);

    /**
     * 批量更新知识库状态
     *
     * @param ids      知识库 ID 列表
     * @param isActive 是否激活
     * @return 影响行数
     */
    boolean batchUpdateStatus(List<Long> ids, Boolean isActive);

    /**
     * 逻辑删除知识库
     *
     * @param id 知识库 ID
     * @return 是否成功
     */
    boolean logicDelete(Long id);

    /**
     * 创建知识库
     *
     * @param name        知识库名称
     * @param description 知识库描述
     * @return 创建的知识库
     */
    KnowledgeBase createKnowledgeBase(String name, String description);
}
