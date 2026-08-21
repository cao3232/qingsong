package com.qingsong.ai.service.knowledge;

import com.baomidou.mybatisplus.extension.service.IService;
import com.qingsong.ai.entity.po.knowledge.DocumentBase;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;

import java.util.List;

/**
 * 文档 Service 接口
 *
 * @author AI Architect
 * @since 2026-03-20
 */
public interface DocumentService extends IService<DocumentBase> {

    /**
     * 根据知识库 ID 查询所有文档
     *
     * @param knowledgeId 知识库 ID
     * @return 文档列表
     */
    List<DocumentBase> getByKnowledgeId(Long knowledgeId);

    /**
     * 查询指定知识库下未嵌入向量的文档
     *
     * @param knowledgeId 知识库 ID
     * @return 文档列表
     */
    List<DocumentBase> getNotEmbeddedDocuments(Long knowledgeId);

    /**
     * 统计知识库的文档总数
     *
     * @param knowledgeId 知识库 ID
     * @return 文档数量
     */
    Long countByKnowledgeId(Long knowledgeId);

    /**
     * 根据文件名称模糊查询文档
     *
     * @param knowledgeId 知识库 ID
     * @param fileName    文件名关键词
     * @return 文档列表
     */
    List<DocumentBase> searchByFileName(Long knowledgeId, String fileName);


    /**
     * 添加文档到知识库
     *
     * @param knowledgeId 知识库 ID
     * @param fileName    文件名
     * @param path        文件路径
     * @param sourceId    来源 ID
     * @return 创建的文档
     */
    DocumentBase addDocument(Long knowledgeId, String fileName, String path, String sourceId);

    /**
     * 标记文档为已嵌入向量
     *
     * @param documentId 文档 ID
     * @return 是否成功
     */
    boolean markAsEmbedded(Long documentId);

    boolean deleteByKnowledgeInfo(Long knowledgeId, Long documentId);

    ResponseEntity<InputStreamResource> downloadDocument(Long documentId);
}
