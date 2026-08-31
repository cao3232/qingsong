package com.qingsong.ai.service.impl.knowledge;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qingsong.ai.context.UserContext;
import com.qingsong.ai.entity.exception.BusinessException;
import com.qingsong.ai.entity.po.knowledge.DocumentBase;
import com.qingsong.ai.entity.po.knowledge.OriginFileSource;
import com.qingsong.ai.mapper.knowledge.DocumentMapper;
import com.qingsong.ai.mapper.knowledge.OriginFileSourceMapper;
import com.qingsong.ai.service.knowledge.OriginFileSourceService;
import com.qingsong.ai.service.originfile.ObjectStoreService;
import com.qingsong.ai.service.originfile.StorageFile;
import com.qingsong.ai.utils.OriginFileUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class OriginFileSourceServiceImpl extends ServiceImpl<OriginFileSourceMapper, OriginFileSource> implements OriginFileSourceService {

    @Autowired
    private DocumentMapper documentMapper;

    @Autowired
    private ObjectStoreService objectStoreService;

    @Autowired
    private TokenTextSplitter tokenTextSplitter;

    @Autowired
    private VectorStore vectorStore;

    public static final String KNOWLEDGE_BUCKET_NAME = "knowledge-file";

    /**
     * 单文件大小上限（50MB，与前端声明一致）
     */
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;

    /**
     * 允许上传的文件扩展名白名单
     */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "doc", "docx", "txt", "md", "markdown");

    @Override
    public OriginFileSource getByMd5(String md5) {
        log.debug("根据 MD5 查询文件源：{}", md5);
        return this.lambdaQuery()
                .eq(OriginFileSource::getMd5, md5)
                .one();
    }

    @Override
    public OriginFileSource getByBucketAndObject(String bucketName, String objectName) {
        log.debug("根据对象存储路径查询文件源，bucket: {}, object: {}", bucketName, objectName);
        return this.lambdaQuery()
                .eq(OriginFileSource::getBucketName, bucketName)
                .eq(OriginFileSource::getObjectName, objectName)
                .one();
    }

    @Override
    public Long countTotalFiles() {
        log.debug("统计文件总数");
        return this.lambdaQuery().count();
    }

    @Override
    public List<OriginFileSource> getByIds(List<String> ids) {
        log.info("根据 ID 列表批量查询文件源，ids: {}", ids);
        return this.lambdaQuery()
                .in(OriginFileSource::getId, ids)
                .list();
    }

    @Override
    public boolean updateImages(String id, String images) {
        log.info("更新文件图片列表，id: {}", id);
        OriginFileSource updateEntity = new OriginFileSource();
        updateEntity.setId(id);
        updateEntity.setImages(images);
        updateEntity.setUpdateDate(LocalDateTime.now());
        return this.updateById(updateEntity);
    }

    @Override
    public boolean saveFileSource(OriginFileSource fileSource) {
        log.info("保存文件源信息，fileName: {}, size: {}",
                fileSource.getFileName(), fileSource.getSize());

        if (StrUtil.isBlank(fileSource.getId())) {
            fileSource.setCreateDate(LocalDateTime.now());
        }
        fileSource.setUpdateDate(LocalDateTime.now());

        return this.saveOrUpdate(fileSource);
    }

    @Override
    public boolean existsByMd5(String md5) {
        log.debug("检查文件是否存在，md5: {}", md5);
        Long count = this.lambdaQuery()
                .eq(OriginFileSource::getMd5, md5)
                .count();
        return count != null && count > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean uploadFile(MultipartFile file, String knowledgeId) {
        validateUploadFile(file);

        File tmpFile = null;
        try {
            tmpFile = OriginFileUtil.createTempFile("know", "_" + file.getOriginalFilename());
            file.transferTo(tmpFile);
            String md5 = OriginFileUtil.md5(tmpFile);
            if (existsByMd5(md5)) {
                throw new BusinessException("文件已存在，请勿重复上传");
            }

            String bucketName = KNOWLEDGE_BUCKET_NAME;
            String objectName = UUID.randomUUID().toString().replace("-", "") + "-" + file.getOriginalFilename();
            String newObjectName = String.format("%s/%s", objectName, OriginFileUtil.generatorFileId(bucketName, objectName));

            boolean minioUploaded = false;
            boolean vectorsWritten = false;
            Long documentId = null;
            try {
                String path = objectStoreService.uploadFile(tmpFile, bucketName, newObjectName);
                minioUploaded = true;
                StorageFile fileInfo = objectStoreService.getFileInfo(bucketName, newObjectName);

                OriginFileSource upload = new OriginFileSource();
                upload.setMd5(md5);
                upload.setFileName(file.getOriginalFilename());
                upload.setPath(path);
                upload.setId(fileInfo.getId());
                upload.setBucketName(bucketName);
                upload.setObjectName(newObjectName);
                upload.setSize(fileInfo.getSize());
                upload.setContentType(fileInfo.getContentType());
                this.saveOrUpdate(upload);

                DocumentBase documentEntity = new DocumentBase();
                documentEntity.setFileName(file.getOriginalFilename());
                documentEntity.setKnowledgeId(Long.valueOf(knowledgeId));
                documentEntity.setPath(upload.getPath());
                documentEntity.setEmbedding(false);
                documentEntity.setSourceId(upload.getId());
                documentMapper.insert(documentEntity);
                documentId = documentEntity.getId();

                List<Document> splitDocumentList = parseAndSplit(upload);
                List<Document> hasMetaDocumentList = buildMetaDocumentList(splitDocumentList,
                        documentEntity.getKnowledgeId(), documentEntity.getId(), documentEntity.getFileName());

                if (!hasMetaDocumentList.isEmpty()) {
                    log.info("向量化文档，数量：{}", hasMetaDocumentList.size());
                    vectorStore.accept(hasMetaDocumentList);
                    vectorsWritten = true;
                    markDocumentEmbedded(documentEntity.getId());
                } else {
                    log.warn("文档无可抽取文本，保持待处理状态：{}", file.getOriginalFilename());
                }
                return true;
            } catch (Exception e) {
                // 任一步失败：尽力清理已写入的向量与 MinIO 对象，DB 侧由事务回滚
                if (vectorsWritten && documentId != null) {
                    cleanupVectors(documentId);
                }
                if (minioUploaded) {
                    cleanupMinio(bucketName, newObjectName);
                }
                throw e;
            }
        } catch (BusinessException e) {
            log.warn("上传被拒绝：{}", e.getMessage());
            throw e;
        } catch (IOException e) {
            log.error("上传文件失败：{}", file.getOriginalFilename(), e);
            throw new BusinessException("上传文件失败");
        } finally {
            if (tmpFile != null) {
                tmpFile.delete();
            }
        }
    }

    @Override
    public boolean embedDocument(DocumentBase document) {
        OriginFileSource fileSource = this.getById(document.getSourceId());
        if (fileSource == null) {
            log.warn("文件源不存在，跳过嵌入：documentId={}", document.getId());
            return false;
        }
        List<Document> splitList;
        try {
            splitList = parseAndSplit(fileSource);
        } catch (IOException e) {
            log.error("解析文件失败，documentId={}", document.getId(), e);
            return false;
        }
        List<Document> metaDocs = buildMetaDocumentList(splitList,
                document.getKnowledgeId(), document.getId(), document.getFileName());
        if (metaDocs.isEmpty()) {
            log.warn("文档无可抽取文本，documentId={}", document.getId());
            return false;
        }
        // 先清理旧向量，避免重复写入
        cleanupVectors(document.getId());
        try {
            vectorStore.accept(metaDocs);
        } catch (Exception e) {
            log.error("向量化失败，documentId={}", document.getId(), e);
            return false;
        }
        markDocumentEmbedded(document.getId());
        log.info("文档嵌入完成，documentId={}, chunks={}", document.getId(), metaDocs.size());
        return true;
    }

    /**
     * 构建注入元数据后的向量文档列表（含用户、知识库、文档、文件信息）
     */
    private List<Document> buildMetaDocumentList(List<Document> splitList,
                                                 Long knowledgeId, Long documentId, String fileName) {
        return splitList.stream().map(item -> {
            Map<String, Object> metadata = item.getMetadata();
            metadata.put("user_id", currentUserId());
            metadata.put("knowledge_base_id", String.valueOf(knowledgeId));
            metadata.put("document_id", documentId);
            if (StrUtil.isNotBlank(fileName)) {
                metadata.put("file_name", fileName);
            }
            return new Document(item.getText(), metadata);
        }).toList();
    }

    /**
     * 将文档标记为已嵌入向量
     */
    private void markDocumentEmbedded(Long documentId) {
        DocumentBase updateEntity = new DocumentBase();
        updateEntity.setId(documentId);
        updateEntity.setEmbedding(true);
        documentMapper.updateById(updateEntity);
    }

    @Override
    public boolean deleteFile(OriginFileSource fileSource) {
        OriginFileSource query = this.lambdaQuery()
                .eq(OriginFileSource::getFileName, fileSource.getFileName())
                .eq(OriginFileSource::getPath, fileSource.getPath())
                .one();
        if (query == null) {
            log.info("文件不存在，fileName: {}, path: {}", fileSource.getFileName(), fileSource.getPath());
            return false;
        }

        if (this.removeById(query.getId())) {
            try {
                objectStoreService.deleteFile(query.getBucketName(), query.getObjectName());
            } catch (Exception e) {
                log.error("删除 MinIO 对象失败，fileName: {}, bucket: {}, object: {}",
                        query.getFileName(), query.getBucketName(), query.getObjectName(), e);
            }
            return true;
        }
        return false;
    }

    @Override
    public InputStream downloadFileByDocument(DocumentBase document) {
        OriginFileSource query = this.lambdaQuery()
                .eq(OriginFileSource::getFileName, document.getFileName())
                .eq(OriginFileSource::getPath, document.getPath())
                .one();
        if (query == null) {
            log.info("文件不存在，fileName: {}, path: {}", document.getFileName(), document.getPath());
            return null;
        }
        return objectStoreService.getFile(query.getBucketName(), query.getObjectName());
    }

    /**
     * 校验上传文件：文件非空、大小不超过 50MB、扩展名在白名单内
     */
    private void validateUploadFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("文件大小不能超过 50MB");
        }
        String extension = getExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException("不支持的文件类型：" + extension + "，仅支持 pdf/doc/docx/txt/md/markdown");
        }
    }

    /**
     * 提取文件名扩展名（小写）
     */
    private String getExtension(String fileName) {
        if (StrUtil.isBlank(fileName)) {
            return "";
        }
        int idx = fileName.lastIndexOf('.');
        return idx < 0 ? "" : fileName.substring(idx + 1).toLowerCase();
    }

    /**
     * 获取当前登录用户 ID，未登录时回退 root
     */
    private String currentUserId() {
        Long userId = UserContext.getCurrentUserId();
        return userId != null ? String.valueOf(userId) : "root";
    }

    /**
     * 读取对象存储中的文件并做 token 切分
     */
    private List<Document> parseAndSplit(OriginFileSource upload) throws IOException {
        Resource resource;
        try (InputStream inputStream = objectStoreService.getFile(upload.getBucketName(), upload.getObjectName())) {
            resource = new ByteArrayResource(inputStream.readAllBytes());
        }
        TikaDocumentReader tikaDocumentReader = new TikaDocumentReader(resource);
        List<Document> rawDocumentList = tikaDocumentReader.read();
        return tokenTextSplitter.split(rawDocumentList);
    }

    /**
     * 向量化失败时，尽力清理该文档已写入的向量
     */
    private void cleanupVectors(Long documentId) {
        try {
            Filter.Expression filterExpression = new FilterExpressionBuilder()
                    .eq("document_id", documentId)
                    .build();
            vectorStore.delete(filterExpression);
            log.info("已清理向量: documentId={}", documentId);
        } catch (Exception e) {
            log.error("清理向量失败: documentId={}", documentId, e);
        }
    }

    /**
     * 上传失败时，尽力删除已写入 MinIO 的对象
     */
    private void cleanupMinio(String bucketName, String objectName) {
        try {
            objectStoreService.deleteFile(bucketName, objectName);
            log.info("已清理 MinIO 对象: bucket={}, object={}", bucketName, objectName);
        } catch (Exception e) {
            log.error("清理 MinIO 对象失败: bucket={}, object={}", bucketName, objectName, e);
        }
    }
}
