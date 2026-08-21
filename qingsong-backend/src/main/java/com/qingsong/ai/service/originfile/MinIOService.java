package com.qingsong.ai.service.originfile;

import com.qingsong.ai.config.MinioProperties;
import com.qingsong.ai.entity.po.knowledge.OriginFileSource;
import com.qingsong.ai.mapper.knowledge.OriginFileSourceMapper;
import com.qingsong.ai.utils.OriginFileUtil;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.http.Method;
import io.minio.messages.Item;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * @Project: me.pgthinker.objectstore.service
 * @Author: NingNing0111
 * @Github: https://github.com/ningning0111
 * @Date: 2025/3/29 02:38
 * @Description:
 */
@Slf4j
@Service
public class MinIOService implements ObjectStoreService {

    private final MinioProperties minioProperties;

    private final MinioClient minioClient;

    private final OriginFileSourceMapper originFileResourceMapper;

    /**
     * 初始化MinioClient
     */
    public MinIOService(MinioProperties minioProperties, OriginFileSourceMapper originFileResourceMapper) {
        this.minioProperties = minioProperties;
        this.minioClient = MinioClient.builder()
                .endpoint(minioProperties.getEndpoint())
                .credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey())
                .build();
        this.originFileResourceMapper = originFileResourceMapper;
    }

    @Override
    public String uploadFile(MultipartFile file, String bucketName, String objectName) {
        try {
            return uploadFile(file.getInputStream(), file.getSize(), bucketName, objectName, file.getContentType());
        } catch (Exception e) {
            log.error("File upload failed", e);
            throw new RuntimeException("File upload failed: " + e.getMessage());
        }
    }

    @Override
    public String uploadFile(InputStream inputstream, long fileSize, String bucketName, String objectName,
                             String contentType) {
        try {
            // 检查并创建存储桶
            if (!bucketExists(bucketName)) {
                createBucket(bucketName);
                log.info("Created bucket: {}", bucketName);
            }
            // 上传文件
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(inputstream, fileSize, -1)
                    .contentType(contentType)
                    .build());

            log.info("File uploaded to {}/{}", bucketName, objectName);
            return bucketName + "/" + objectName;
        } catch (Exception e) {
            log.error("File upload failed", e);
            throw new RuntimeException("File upload failed: " + e.getMessage());
        }
    }

    @Override
    public String uploadFile(MultipartFile file, String objectName) {
        return uploadFile(file, minioProperties.getDefaultBucket(), objectName);
    }

    @Override
    public String getTmpFileUrl(String bucketName, String objectName, int expirySeconds) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucketName)
                    .object(objectName)
                    .expiry(expirySeconds)
                    .build());
        } catch (Exception e) {
            log.error("Get file URL failed", e);
            throw new RuntimeException("Failed to generate file URL");
        }
    }

    @Override
    public String getTmpFileUrl(String bucketName, String objectName) {
        try {
            return getTmpFileUrl(bucketName, objectName, minioProperties.getDefaultExpiry());
        } catch (Exception e) {
            log.error("Get file URL failed", e);
            throw new RuntimeException("Failed to generate file URL");
        }
    }

    @Override
    public InputStream getFile(String bucketName, String objectName) {
        try {
            return minioClient.getObject(GetObjectArgs.builder().bucket(bucketName).object(objectName).build());
        } catch (Exception e) {
            log.error("Get file failed", e);
            throw new RuntimeException("File retrieval failed");
        }
    }

    @Override
    public void deleteFile(String bucketName, String objectName) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucketName).object(objectName).build());
            log.info("Deleted {}/{}", bucketName, objectName);
        } catch (Exception e) {
            log.error("Delete file failed", e);
            throw new RuntimeException("File deletion failed");
        }
    }

    @Override
    public List<String> listFiles(String bucketName) {
        List<String> objectNames = new ArrayList<>();
        try {
            Iterable<Result<Item>> results = minioClient
                    .listObjects(ListObjectsArgs.builder().bucket(bucketName).build());

            for (Result<Item> result : results) {
                objectNames.add(result.get().objectName());
            }
            return objectNames;
        } catch (Exception e) {
            log.error("List files failed", e);
            throw new RuntimeException("Failed to list objects");
        }
    }

    @Override
    public StorageFile getFileInfo(String bucketName, String objectName) {
        try {
            OriginFileSource originFileResource = originFileResourceMapper
                    .selectById(OriginFileUtil.generatorFileId(bucketName, objectName));
            if (originFileResource != null) {
                return originFileResource;
            }
            StatObjectResponse stat = minioClient
                    .statObject(StatObjectArgs.builder().bucket(bucketName).object(objectName).build());
            OriginFileSource newOriginFileResource = new OriginFileSource();
            newOriginFileResource.setId(OriginFileUtil.generatorFileId(bucketName, objectName));
            newOriginFileResource.setObjectName(objectName);
            newOriginFileResource.setBucketName(bucketName);
            newOriginFileResource.setFileName(objectName);
            newOriginFileResource.setPath("/" + bucketName + "/" + objectName);
            newOriginFileResource.setContentType(stat.contentType());
            newOriginFileResource.setSize(stat.size());
            // newOriginFileResource.setIsImage(stat.contentType().startsWith("image/"));
            return newOriginFileResource;
        } catch (Exception e) {
            log.error("Get file info failed", e);
            throw new RuntimeException("Failed to get file metadata");
        }
    }

    @Override
    public boolean bucketExists(String bucketName) {
        try {
            return minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
        } catch (Exception e) {
            log.error("Check bucket existence failed", e);
            throw new RuntimeException("Bucket check failed");
        }
    }

    @Override
    public void createBucket(String bucketName) {
        try {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
        } catch (Exception e) {
            log.error("Create bucket failed", e);
            throw new RuntimeException("Bucket creation failed");
        }
    }

}
