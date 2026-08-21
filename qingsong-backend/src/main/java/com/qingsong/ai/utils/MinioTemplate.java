package com.qingsong.ai.utils;

    import com.qingsong.ai.config.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.DownloadObjectArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.UploadObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * description...
 *
 * @author : caojiangjiang
 * @data : 2026/08/21 14:09
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MinioTemplate {

    private final MinioClient client;
    private final MinioProperties properties;

    /** 统一异常包装，调用方再也不用管受检异常 */
    public static class MinioException extends RuntimeException {
        public MinioException(String message, Throwable cause) {
            super(message, cause);
        }
    }


    // ========== 上传 ==========

    public String putObject(String objectName, InputStream in, String contentType) {
        return putObject(properties.getDefaultBucket(), objectName, in, contentType);
    }

    public String putObject(String bucket, String objectName, InputStream in, String contentType) {
        long start = System.currentTimeMillis();
        try {
            ensureBucketExists(bucket);
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .contentType(contentType)
                    .stream(in, in.available(), -1)   // 注意：in.available() 不准时传 -1
                    .build());
            log.info("MinIO 上传成功 bucket={}, object={}, 耗时={}ms",
                    bucket, objectName, System.currentTimeMillis() - start);
            return resolveUrl(bucket, objectName);
        } catch (Exception e) {
            log.error("MinIO 上传失败 bucket={}, object={}", bucket, objectName, e);
            throw new MinioException("上传对象失败: " + objectName, e);
        }
    }

    /** 上传本地文件 */
    public void uploadObject(String objectName, String filePath) {
        try {
            client.uploadObject(UploadObjectArgs.builder()
                    .bucket(properties.getDefaultBucket())
                    .object(objectName)
                    .filename(filePath)
                    .build());
        } catch (Exception e) {
            throw new MinioException("上传文件失败: " + filePath, e);
        }
    }

    /** 上传内存字节流并返回预签名下载 URL（不落本地，供工具直接返回给 AI） */
    public String putObjectAndGetPresignedUrl(String objectName, InputStream in, long size,
                                              String contentType, int expirySeconds) {
        String bucket = properties.getDefaultBucket();
        long start = System.currentTimeMillis();
        try {
            ensureBucketExists(bucket);
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .contentType(contentType)
                    .stream(in, size, -1)
                    .build());
            log.info("MinIO 上传成功并生成签名 bucket={}, object={}, 耗时={}ms",
                    bucket, objectName, System.currentTimeMillis() - start);
            return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(objectName)
                    .expiry(expirySeconds)
                    .build());
        } catch (Exception e) {
            log.error("上传并生成签名 URL 失败: bucket={}, object={}", bucket, objectName, e);
            throw new MinioException("上传并生成签名 URL 失败: " + objectName, e);
        }
    }

    // ========== 下载 ==========

    /** 返回流，调用方负责关闭 */
    public InputStream getObject(String objectName) {
        return getObject(properties.getDefaultBucket(), objectName);
    }

    public InputStream getObject(String bucket, String objectName) {
        try {
            return client.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .build());
        } catch (Exception e) {
            throw new MinioException("获取对象失败: " + objectName, e);
        }
    }

    public void downloadObject(String objectName, String filePath) {
        try {
            client.downloadObject(DownloadObjectArgs.builder()
                    .bucket(properties.getDefaultBucket())
                    .object(objectName)
                    .filename(filePath)
                    .build());
        } catch (Exception e) {
            throw new MinioException("下载对象失败: " + objectName, e);
        }
    }

    // ========== 预签名 ==========

    /** 临时下载 URL */
    public String presignedGetUrl(String objectName, int expirySeconds) {
        try {
            return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(properties.getDefaultBucket())
                    .object(objectName)
                    .expiry(expirySeconds)
                    .build());
        } catch (Exception e) {
            throw new MinioException("生成预签名下载 URL 失败: " + objectName, e);
        }
    }

    /** 临时上传 URL（前端直传场景） */
    public String presignedPutUrl(String objectName, int expirySeconds) {
        try {
            return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.PUT)
                    .bucket(properties.getDefaultBucket())
                    .object(objectName)
                    .expiry(expirySeconds)
                    .build());
        } catch (Exception e) {
            throw new MinioException("生成预签名上传 URL 失败: " + objectName, e);
        }
    }

    // ========== 元信息 & 删除 ==========

    public boolean exists(String objectName) {
        try {
            client.statObject(StatObjectArgs.builder()
                    .bucket(properties.getDefaultBucket())
                    .object(objectName)
                    .build());
            return true;
        } catch (ErrorResponseException e) {
            return false;   // 404，对象不存在
        } catch (Exception e) {
            throw new MinioException("检查对象失败: " + objectName, e);
        }
    }

    public void removeObject(String objectName) {
        try {
            client.removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.getDefaultBucket())
                    .object(objectName)
                    .build());
        } catch (Exception e) {
            throw new MinioException("删除对象失败: " + objectName, e);
        }
    }

    // ========== 私有方法 ==========

    private void ensureBucketExists(String bucket) throws Exception {
        boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }

    /** 外部可读的完整 URL（需配合桶策略，否则只能内网访问） */
    private String resolveUrl(String bucket, String objectName) {
        return properties.getEndpoint() + "/" + bucket + "/" + objectName;
    }
}
