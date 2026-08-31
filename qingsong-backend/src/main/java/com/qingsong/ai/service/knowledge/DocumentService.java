package com.qingsong.ai.service.knowledge;

import com.baomidou.mybatisplus.extension.service.IService;
import com.qingsong.ai.entity.po.knowledge.DocumentBase;
import com.qingsong.ai.entity.vo.knowledge.DocumentPageResp;
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
     * 分页查询知识库文档（含总大小、已嵌入数聚合，支持按嵌入状态与文件类型筛选）
     *
     * @param knowledgeId 知识库 ID
     * @param embedding   嵌入状态过滤（可空）
     * @param fileType    文件类型过滤（pdf/word/txt/md，可空）
     * @param pageNum     页码
     * @param pageSize    每页数量
     * @return 文档分页数据
     */
    DocumentPageResp pageByKnowledgeId(Long knowledgeId, Boolean embedding, String fileType, long pageNum, long pageSize);

    /**
     * 分页搜索知识库文档（含总大小、已嵌入数聚合，支持按嵌入状态与文件类型筛选）
     *
     * @param knowledgeId 知识库 ID
     * @param fileName    文件名关键词
     * @param embedding   嵌入状态过滤（可空）
     * @param fileType    文件类型过滤（pdf/word/txt/md，可空）
     * @param pageNum     页码
     * @param pageSize    每页数量
     * @return 文档分页数据
     */
    DocumentPageResp pageSearchByFileName(Long knowledgeId, String fileName, Boolean embedding, String fileType,
                                          long pageNum, long pageSize);

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

    /**
     * 级联删除知识库下的全部文档（含向量与原始文件）
     *
     * @param knowledgeId 知识库 ID
     * @return 是否成功
     */
    boolean deleteByKnowledgeId(Long knowledgeId);

    /**
     * 查询未嵌入向量的文档（knowledgeId 为 null 时查询全部）
     *
     * @param knowledgeId 知识库 ID（可空）
     * @return 待嵌入文档列表
     */
    List<DocumentBase> listNotEmbedded(Long knowledgeId);

    /**
     * 查询未嵌入向量的文档，限制返回条数（knowledgeId 为 null 时查询全部）
     *
     * @param knowledgeId 知识库 ID（可空）
     * @param limit       返回条数上限
     * @return 待嵌入文档列表
     */
    List<DocumentBase> listNotEmbedded(Long knowledgeId, int limit);

    /**
     * 重新嵌入单个文档
     *
     * @param documentId 文档 ID
     * @return 是否成功
     */
    boolean reEmbedDocument(Long documentId);

    /**
     * 重新嵌入某知识库下所有待处理文档
     *
     * @param knowledgeId 知识库 ID（可空，为空时处理全部）
     * @return 成功条数
     */
    long reEmbedPending(Long knowledgeId);

    ResponseEntity<InputStreamResource> downloadDocument(Long documentId);
}
