package com.qingsong.ai.controller.knowledge;

import com.qingsong.ai.entity.vo.knowledge.DocumentPageResp;
import com.qingsong.ai.service.knowledge.DocumentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 文档 Controller
 *
 * @author AI Architect
 * @since 2026-03-20
 */
@RestController
@RequestMapping("/api/knowledge/documents")
public class DocumentController {

    @Autowired
    private DocumentService documentService;

    /**
     * 分页获取知识库的所有文档（支持按嵌入状态、文件类型筛选）
     */
    @GetMapping("/knowledge/{knowledgeId}")
    public DocumentPageResp getDocumentsByKnowledge(@PathVariable Long knowledgeId,
                                                    @RequestParam(required = false) Boolean embedding,
                                                    @RequestParam(required = false) String fileType,
                                                    @RequestParam(defaultValue = "1") long pageNum,
                                                    @RequestParam(defaultValue = "10") long pageSize) {
        return documentService.pageByKnowledgeId(knowledgeId, embedding, fileType, pageNum, pageSize);
    }

    /**
     * 分页搜索文档（支持按嵌入状态、文件类型筛选）
     */
    @GetMapping("/search")
    public DocumentPageResp searchDocuments(@RequestParam Long knowledgeId,
                                            @RequestParam String fileName,
                                            @RequestParam(required = false) Boolean embedding,
                                            @RequestParam(required = false) String fileType,
                                            @RequestParam(defaultValue = "1") long pageNum,
                                            @RequestParam(defaultValue = "10") long pageSize) {
        return documentService.pageSearchByFileName(knowledgeId, fileName, embedding, fileType, pageNum, pageSize);
    }

    /**
     * 重新嵌入单个文档
     */
    @PostMapping("/{documentId}/reembed")
    public boolean reEmbedDocument(@PathVariable Long documentId) {
        return documentService.reEmbedDocument(documentId);
    }

    /**
     * 重新嵌入某知识库下所有待处理文档
     */
    @PostMapping("/knowledge/{knowledgeId}/reembed-pending")
    public long reEmbedPending(@PathVariable Long knowledgeId) {
        return documentService.reEmbedPending(knowledgeId);
    }

    /**
     * 根据文档id删除知识库文档
     */
    @DeleteMapping("/{knowledgeId}/{documentId}")
    public boolean deleteDocumentsByKnowledge(@PathVariable Long knowledgeId, @PathVariable Long documentId) {
        return documentService.deleteByKnowledgeInfo(knowledgeId, documentId);
    }

    /**
     * 下载文档
     *
     * @return
     */
    @GetMapping("/{documentId}/download")
    public ResponseEntity<InputStreamResource> downloadDocument(@PathVariable Long documentId) {
        return documentService.downloadDocument(documentId);

    }


}
