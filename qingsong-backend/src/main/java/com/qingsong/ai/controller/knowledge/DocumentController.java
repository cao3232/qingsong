package com.qingsong.ai.controller.knowledge;

import com.qingsong.ai.entity.po.knowledge.DocumentBase;
import com.qingsong.ai.service.knowledge.DocumentService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
     * 获取知识库的所有文档
     */
    @GetMapping("/knowledge/{knowledgeId}")
    public List<DocumentBase> getDocumentsByKnowledge(@PathVariable Long knowledgeId) {
        return documentService.getByKnowledgeId(knowledgeId);
    }

    /**
     * 搜索文档
     */
    @GetMapping("/search")
    public List<DocumentBase> searchDocuments(
            @RequestParam Long knowledgeId,
            @RequestParam String fileName) {
        return documentService.searchByFileName(knowledgeId, fileName);
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
