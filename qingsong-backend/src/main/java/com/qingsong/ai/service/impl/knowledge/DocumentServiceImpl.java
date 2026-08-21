package com.qingsong.ai.service.impl.knowledge;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qingsong.ai.entity.exception.BusinessException;
import com.qingsong.ai.entity.po.knowledge.DocumentBase;
import com.qingsong.ai.entity.po.knowledge.OriginFileSource;
import com.qingsong.ai.mapper.knowledge.DocumentMapper;
import com.qingsong.ai.service.knowledge.DocumentService;
import com.qingsong.ai.service.knowledge.OriginFileSourceService;
import com.qingsong.ai.service.originfile.ObjectStoreService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class DocumentServiceImpl extends ServiceImpl<DocumentMapper, DocumentBase> implements DocumentService {

    @Autowired
    private OriginFileSourceService originFileSourceService;

    @Autowired
    private ObjectStoreService objectStoreService;

    @Autowired
    private VectorStore vectorStore;

    @Override
    public List<DocumentBase> getByKnowledgeId(Long knowledgeId) {
        log.info("查询知识库文档：{}", knowledgeId);
        return this.lambdaQuery()
                .eq(DocumentBase::getKnowledgeId, knowledgeId)
                .orderByDesc(DocumentBase::getCreateDate)
                .list();
    }

    @Override
    public List<DocumentBase> getNotEmbeddedDocuments(Long knowledgeId) {
        log.info("查询未嵌入向量的文档：{}", knowledgeId);
        return this.lambdaQuery()
                .eq(DocumentBase::getKnowledgeId, knowledgeId)
                .eq(DocumentBase::getEmbedding, false)
                .orderByAsc(DocumentBase::getCreateDate)
                .list();
    }

    @Override
    public Long countByKnowledgeId(Long knowledgeId) {
        log.info("统计知识库文档数量：{}", knowledgeId);
        return this.lambdaQuery()
                .eq(DocumentBase::getKnowledgeId, knowledgeId)
                .count();
    }

    @Override
    public List<DocumentBase> searchByFileName(Long knowledgeId, String fileName) {
        log.info("搜索文档，knowledgeId: {}, fileName: {}", knowledgeId, fileName);
        return this.lambdaQuery()
                .eq(DocumentBase::getKnowledgeId, knowledgeId)
                .like(DocumentBase::getFileName, fileName)
                .orderByDesc(DocumentBase::getCreateDate)
                .list();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentBase addDocument(Long knowledgeId, String fileName, String path, String sourceId) {
        log.info("添加文档到知识库，knowledgeId: {}, fileName: {}", knowledgeId, fileName);
        DocumentBase document = new DocumentBase();
        document.setKnowledgeId(knowledgeId);
        document.setFileName(fileName);
        document.setPath(path);
        document.setSourceId(sourceId);
        document.setEmbedding(false);
        document.setCreateDate(LocalDateTime.now());
        document.setUpdateDate(LocalDateTime.now());

        this.save(document);
        log.info("文档添加成功，ID: {}", document.getId());
        return document;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean markAsEmbedded(Long documentId) {
        log.info("标记文档为已嵌入向量：{}", documentId);
        DocumentBase updateEntity = new DocumentBase();
        updateEntity.setId(documentId);
        updateEntity.setEmbedding(true);
        updateEntity.setUpdateDate(LocalDateTime.now());
        return this.updateById(updateEntity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteByKnowledgeInfo(Long knowledgeId, Long documentId) {
        log.info("删除知识库{}下的文档{}", knowledgeId, documentId);
        DocumentBase query = this.lambdaQuery()
                .eq(DocumentBase::getId, documentId)
                .eq(DocumentBase::getKnowledgeId, knowledgeId)
                .one();
        if (query == null) {
            log.warn("文档不存在，ID: {}", documentId);
            return false;
        }
        if (query.getEmbedding()) {
            try {
                Filter.Expression filterExpression = new FilterExpressionBuilder()
                        .eq("document_id", query.getId())
                        .build();
                vectorStore.delete(filterExpression);
                log.info("向量库数据已清理: docId={}", documentId);
            } catch (Exception e) {
                log.error("向量库删除异常: {}", e.getMessage());
                throw new BusinessException("清理向量数据失败，请稍后重试");
            }
        }

        OriginFileSource originFileSource = new OriginFileSource();
        originFileSource.setFileName(query.getFileName());
        originFileSource.setPath(query.getPath());
        boolean fileDeleted = originFileSourceService.deleteFile(originFileSource);
        if (!fileDeleted) {
            log.warn("删除原始文件源失败：{}", query.getFileName());
            throw new BusinessException("删除原始文件源失败");
        }

        return this.lambdaUpdate()
                .eq(DocumentBase::getId, documentId)
                .eq(DocumentBase::getKnowledgeId, knowledgeId)
                .remove();
    }

    @Override
    public ResponseEntity<InputStreamResource> downloadDocument(Long documentId) {
        DocumentBase document = this.lambdaQuery()
                .eq(DocumentBase::getId, documentId)
                .one();
        if (document == null) {
            log.warn("文档不存在，ID: {}", documentId);
            throw new BusinessException("文档不存在");
        }
        InputStream inputStream = originFileSourceService.downloadFileByDocument(document);
        if (inputStream == null) {
            log.warn("无法获取文件流，文档 ID: {}", documentId);
            throw new BusinessException("无法读取文件");
        }
        ContentDisposition contentDisposition = ContentDisposition.builder("attachment")
                .filename(document.getFileName(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new InputStreamResource(inputStream));
    }
}
