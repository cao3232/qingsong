package com.qingsong.ai.service.impl.knowledge;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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
        try {
            System.out.println("源文件" + file.getBytes().length);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try {
            OriginFileSource upload = this.upload(file, KNOWLEDGE_BUCKET_NAME);
            DocumentBase documentEntity = new DocumentBase();
            documentEntity.setFileName(file.getOriginalFilename());
            documentEntity.setKnowledgeId(Long.valueOf(knowledgeId));
            documentEntity.setPath(upload.getPath());
            documentEntity.setEmbedding(false);
            documentEntity.setSourceId(upload.getId());
            documentMapper.insert(documentEntity);

            Resource resource;
            try {
                InputStream inputStream = objectStoreService.getFile(upload.getBucketName(), upload.getObjectName());
                byte[] bytes = inputStream.readAllBytes();
                resource = new ByteArrayResource(bytes);
                System.out.println("加载的文件" + bytes.length);
            } catch (IOException e) {
                log.error("获取文件输入流失败：{}", e, e.getMessage());
                throw new BusinessException("获取文件输入流失败");
            }

            TikaDocumentReader tikaDocumentReader = new TikaDocumentReader(resource);
            List<Document> rawDocumentList = tikaDocumentReader.read();

            System.out.println("rawDocumentList size = " + rawDocumentList.size());

            var chunks = tokenTextSplitter.split(rawDocumentList);
            System.out.println("chunks size = " + chunks.size());

            List<Document> splitDocumentList = tokenTextSplitter.split(rawDocumentList);
            List<Document> hasMetaDocumentList = splitDocumentList.stream().map(item -> {
                Map<String, Object> metadata = item.getMetadata();
                metadata.put("user_id", "root");
                metadata.put("knowledge_base_id", knowledgeId);
                metadata.put("document_id", documentEntity.getId());
                return new Document(item.getText(), metadata);
            }).toList();

            if (hasMetaDocumentList.size() > 0) {
                log.info("向量化文档，数量：{}", hasMetaDocumentList.size());
                vectorStore.accept(hasMetaDocumentList);
                documentEntity.setEmbedding(true);
                documentMapper.updateById(documentEntity);
            }
            return true;

        } catch (Exception e) {
            log.error("上传文件失败", e);
        }
        return false;
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
            objectStoreService.deleteFile(query.getBucketName(), query.getObjectName());
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

    private OriginFileSource upload(MultipartFile file, String bucketName) {
        String originalFilename = file.getOriginalFilename();
        String objectName = UUID.randomUUID().toString().replace("-", "") + "-" + originalFilename;
        String id = OriginFileUtil.generatorFileId(bucketName, objectName);
        String newObjectName = String.format("%s/%s", objectName, id);
        String path;
        String md5;
        try {
            File tmpFile = OriginFileUtil.createTempFile("know", "_" + file.getOriginalFilename());
            file.transferTo(tmpFile);
            md5 = OriginFileUtil.md5(tmpFile);
            path = objectStoreService.uploadFile(tmpFile, bucketName, newObjectName);
        } catch (IOException e) {
            log.error("上传文件失败：{}", e, e.getMessage());
            throw new BusinessException("上传文件失败");
        }
        StorageFile fileInfo = objectStoreService.getFileInfo(bucketName, newObjectName);

        OriginFileSource originFileResource = new OriginFileSource();
        originFileResource.setMd5(md5);
        originFileResource.setFileName(originalFilename);
        originFileResource.setPath(path);
        originFileResource.setId(fileInfo.getId());
        originFileResource.setBucketName(bucketName);
        originFileResource.setObjectName(newObjectName);
        originFileResource.setSize(fileInfo.getSize());
        originFileResource.setContentType(fileInfo.getContentType());
        this.saveOrUpdate(originFileResource);
        return originFileResource;
    }


    public static void main(String[] args) {
        try {
            String s = String.class.newInstance();
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }

    }
}
