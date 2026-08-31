package com.qingsong.ai.service.impl.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qingsong.ai.entity.exception.BusinessException;
import com.qingsong.ai.entity.po.knowledge.DocumentBase;
import com.qingsong.ai.entity.po.knowledge.OriginFileSource;
import com.qingsong.ai.entity.vo.knowledge.DocumentPageResp;
import com.qingsong.ai.entity.vo.knowledge.DocumentRespVO;
import com.qingsong.ai.mapper.knowledge.DocumentMapper;
import com.qingsong.ai.service.knowledge.DocumentService;
import com.qingsong.ai.service.knowledge.OriginFileSourceService;
import com.qingsong.ai.service.originfile.ObjectStoreService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.BeanUtils;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
    public DocumentPageResp pageByKnowledgeId(Long knowledgeId, Boolean embedding, String fileType,
                                              long pageNum, long pageSize) {
        log.info("分页查询知识库文档：{}, embedding={}, fileType={}, page={}, size={}",
                knowledgeId, embedding, fileType, pageNum, pageSize);
        return pageDocuments(knowledgeId, null, embedding, fileType, pageNum, pageSize);
    }

    @Override
    public DocumentPageResp pageSearchByFileName(Long knowledgeId, String fileName, Boolean embedding, String fileType,
                                                 long pageNum, long pageSize) {
        log.info("分页搜索文档，knowledgeId: {}, fileName: {}, embedding={}, fileType={}, page={}, size={}",
                knowledgeId, fileName, embedding, fileType, pageNum, pageSize);
        return pageDocuments(knowledgeId, fileName, embedding, fileType, pageNum, pageSize);
    }

    /**
     * 分页查询文档，并聚合匹配集的总大小与已嵌入数量（按筛选条件一致聚合）
     */
    private DocumentPageResp pageDocuments(Long knowledgeId, String fileName, Boolean embedding, String fileType,
                                           long pageNum, long pageSize) {
        IPage<DocumentBase> page = this.page(new Page<>(pageNum, pageSize),
                buildFilterWrapper(knowledgeId, fileName, embedding, fileType)
                        .orderByDesc(DocumentBase::getCreateDate));

        List<String> sourceIds = this.listObjs(buildFilterWrapper(knowledgeId, fileName, embedding, fileType)
                        .select(DocumentBase::getSourceId))
                .stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .toList();
        Map<String, Long> sizeMap = new HashMap<>();
        if (!sourceIds.isEmpty()) {
            originFileSourceService.getByIds(sourceIds)
                    .forEach(src -> sizeMap.put(src.getId(), src.getSize()));
        }
        long totalSize = sizeMap.values().stream()
                .mapToLong(size -> size == null ? 0L : size)
                .sum();
        long embeddedCount = this.count(buildFilterWrapper(knowledgeId, fileName, Boolean.TRUE, fileType));

        List<DocumentRespVO> records = page.getRecords().stream().map(doc -> {
            DocumentRespVO vo = new DocumentRespVO();
            BeanUtils.copyProperties(doc, vo);
            vo.setSize(sizeMap.get(doc.getSourceId()));
            return vo;
        }).toList();

        return DocumentPageResp.builder()
                .total(page.getTotal())
                .totalSize(totalSize)
                .embeddedCount(embeddedCount)
                .records(records)
                .build();
    }

    /**
     * 构造文档过滤条件：知识库 + 文件名模糊 + 嵌入状态 + 文件类型
     */
    private LambdaQueryWrapper<DocumentBase> buildFilterWrapper(Long knowledgeId, String fileName, Boolean embedding,
                                                                String fileType) {
        boolean search = fileName != null && !fileName.isBlank();
        LambdaQueryWrapper<DocumentBase> wrapper = new LambdaQueryWrapper<DocumentBase>()
                .eq(DocumentBase::getKnowledgeId, knowledgeId)
                .like(search, DocumentBase::getFileName, fileName);
        appendFileTypeCondition(wrapper, fileType);
        return wrapper.eq(embedding != null, DocumentBase::getEmbedding, embedding);
    }

    /**
     * 按文件类型追加扩展名匹配条件（word 匹配 doc/docx，md 匹配 md/markdown）
     */
    private void appendFileTypeCondition(LambdaQueryWrapper<DocumentBase> wrapper, String fileType) {
        if (fileType == null || fileType.isBlank()) {
            return;
        }
        String[] extensions = switch (fileType) {
            case "word" -> new String[]{"doc", "docx"};
            case "md" -> new String[]{"md", "markdown"};
            default -> new String[]{fileType};
        };
        wrapper.and(w -> {
            w.like(DocumentBase::getFileName, "." + extensions[0]);
            for (int i = 1; i < extensions.length; i++) {
                w.or().like(DocumentBase::getFileName, "." + extensions[i]);
            }
        });
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
        if (Boolean.TRUE.equals(query.getEmbedding())) {
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
            log.warn("原始文件源不存在或删除失败，继续删除文档：{}", query.getFileName());
        }

        return this.lambdaUpdate()
                .eq(DocumentBase::getId, documentId)
                .eq(DocumentBase::getKnowledgeId, knowledgeId)
                .remove();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteByKnowledgeId(Long knowledgeId) {
        List<DocumentBase> docs = this.lambdaQuery()
                .eq(DocumentBase::getKnowledgeId, knowledgeId)
                .list();
        int removed = 0;
        for (DocumentBase doc : docs) {
            try {
                if (deleteByKnowledgeInfo(knowledgeId, doc.getId())) {
                    removed++;
                }
            } catch (Exception e) {
                log.error("级联删除文档失败，knowledgeId={}, documentId={}", knowledgeId, doc.getId(), e);
            }
        }
        log.info("级联删除文档完成，knowledgeId={}, total={}, removed={}", knowledgeId, docs.size(), removed);
        return true;
    }

    @Override
    public List<DocumentBase> listNotEmbedded(Long knowledgeId) {
        return this.lambdaQuery()
                .eq(knowledgeId != null, DocumentBase::getKnowledgeId, knowledgeId)
                .eq(DocumentBase::getEmbedding, false)
                .orderByAsc(DocumentBase::getCreateDate)
                .list();
    }

    @Override
    public List<DocumentBase> listNotEmbedded(Long knowledgeId, int limit) {
        return this.lambdaQuery()
                .eq(knowledgeId != null, DocumentBase::getKnowledgeId, knowledgeId)
                .eq(DocumentBase::getEmbedding, false)
                .orderByAsc(DocumentBase::getCreateDate)
                .last("LIMIT " + Math.max(limit, 1))
                .list();
    }

    @Override
    public boolean reEmbedDocument(Long documentId) {
        log.info("重新嵌入文档：{}", documentId);
        DocumentBase document = this.getById(documentId);
        if (document == null) {
            log.warn("文档不存在，documentId={}", documentId);
            return false;
        }
        return originFileSourceService.embedDocument(document);
    }

    @Override
    public long reEmbedPending(Long knowledgeId) {
        List<DocumentBase> pending = listNotEmbedded(knowledgeId);
        long success = 0;
        for (DocumentBase doc : pending) {
            try {
                if (reEmbedDocument(doc.getId())) {
                    success++;
                }
            } catch (Exception e) {
                log.error("重新嵌入失败，documentId={}", doc.getId(), e);
            }
        }
        log.info("重新嵌入待处理文档完成，knowledgeId={}, total={}, success={}", knowledgeId, pending.size(), success);
        return success;
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
