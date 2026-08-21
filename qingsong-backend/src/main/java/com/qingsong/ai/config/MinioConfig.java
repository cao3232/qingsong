package com.qingsong.ai.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {

    @Value("${minio.endpoint}")
    private String endpoint;
    @Value("${minio.access-key}")
    private String accessKey;
    @Value("${minio.secret-key}")
    private String secretKey;
    @Value("${minio.default-bucket}")
    private String bucketName;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    // 使用 ApplicationRunner 确保在应用启动后执行，自动创建存储桶
    @Bean
    public ApplicationRunner applicationRunner(MinioClient minioClient) {
        return args -> {
            try {
                boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
                if (!found) {
                    minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                    System.out.println("Bucket '" + bucketName + "' created successfully.");
                } else {
                    System.out.println("Bucket '" + bucketName + "' already exists.");
                }
            } catch (Exception e) {
                System.err.println("Error while creating bucket: " + e.getMessage());
            }
        };
    }
}
