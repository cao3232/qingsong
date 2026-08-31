package com.qingsong.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAG 检索参数配置（application.yaml 的 qingsong.rag.*）
 *
 * @author caojiangjiang
 */
@Data
@Component
@ConfigurationProperties(prefix = "qingsong.rag")
public class RagProperties {

    /**
     * 向量检索返回的切片数量
     */
    private int topK = 5;

    /**
     * 向量检索相似度阈值（越低召回越多，0.3 为宽松值，可用评估集调优）
     */
    private float similarityThreshold = 0.3F;

    /**
     * 文本切分：每块 token 数（中文文档建议 400~1000）
     */
    private int chunkSize = 800;

    /**
     * 文本切分：低于该字符数的块丢弃
     */
    private int minChunkSizeChars = 350;

    /**
     * 文本切分：低于该 token 数的块不嵌入
     */
    private int minChunkLengthToEmbed = 5;

    /**
     * 文本切分：单文档最多切出的块数
     */
    private int maxNumChunks = 10000;
}
