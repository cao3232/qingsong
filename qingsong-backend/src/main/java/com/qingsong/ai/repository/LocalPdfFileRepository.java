package com.qingsong.ai.repository;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Properties;

@Slf4j
@Component
@RequiredArgsConstructor
public class LocalPdfFileRepository implements FileRepository {

    private final VectorStore vectorStore;

    // 会话id 与 文件名的对应关系，方便查询会话历史时重新加载文件
    private final Properties chatFiles = new Properties();

    @Override
    public boolean save(String chatId, Resource resource) {
        // 1.保存到本地磁盘
        String filename = resource.getFilename();
        File target = new File(Objects.requireNonNull(filename));
        if (!target.exists()) {
            try {
                Files.copy(resource.getInputStream(), target.toPath());
            } catch (IOException e) {
                log.error("Failed to save PDF resource.", e);
                return false;
            }
        }
        // 2.保存映射关系
        chatFiles.put(chatId, filename);
        return true;
    }

    @Override
    public Resource getFile(String chatId) {
        return new FileSystemResource(chatFiles.getProperty(chatId));
    }

    @PostConstruct
    private void init() {
        log.info("LocalPdfFileRepository 初始化 - 使用 PgVector 向量存储，跳过本地持久化");
        FileSystemResource pdfResource = new FileSystemResource("chat-pdf.properties");
        if (pdfResource.exists()) {
            try {
                chatFiles.load(new BufferedReader(new InputStreamReader(pdfResource.getInputStream(), StandardCharsets.UTF_8)));
                log.info("已加载 chat-pdf.properties 配置文件");
            } catch (IOException e) {
                log.warn("加载 chat-pdf.properties 失败", e);
            }
        }
        // 注意：PgVector 使用数据库持久化，不需要加载本地的 JSON 文件
        // FileSystemResource vectorResource = new FileSystemResource("chat-pdf.json");
        // if (vectorResource.exists()) {
        //     SimpleVectorStore simpleVectorStore = (SimpleVectorStore) vectorStore;
        //     simpleVectorStore.load(vectorResource);
        // }
    }

    @PreDestroy
    private void persistent() {
        try {
            chatFiles.store(new FileWriter("chat-pdf.properties"), LocalDateTime.now().toString());
            log.info("已保存 chat-pdf.properties 配置文件");
            // 注意：PgVector 使用数据库持久化，不需要保存到本地 JSON 文件
            // SimpleVectorStore simpleVectorStore = (SimpleVectorStore) vectorStore;
            // simpleVectorStore.save(new File("chat-pdf.json"));
        } catch (IOException e) {
            log.error("保存配置文件失败", e);
        }
    }
}
