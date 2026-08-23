package com.qingsong.ai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * RAG 知识库上下文提供器。
 *
 * <p>把知识库检索（向量库查询 + 权限过滤表达式构造）从通用聊天服务中独立出来，
 * 聊天流式主流程只负责装配。调用方需保证 {@code knowledgeBaseIds} 非空。</p>
 *
 * <p>{@code meta} 过滤表达式格式：{@code knowledge_base_id in ["...","..."]}，
 * 与 pgvector 元数据过滤语法对齐；空集合时返回一个恒 false 的表达式，保证不查任何库。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagContextProvider {

    public static final int RAG_TOP_K = 5;

    private final VectorStore vectorStore;

    /**
     * 构建知识库问答 Advisor。
     *
     * @param knowledgeBaseIds 允许访问的知识库 ID（调用方保证非空）
     * @param query            检索 query（通常为当前用户问题）
     */
    public QuestionAnswerAdvisor buildQuestionAnswerAdvisor(List<String> knowledgeBaseIds, String query) {
        SearchRequest searchRequest = SearchRequest.builder()
                .topK(RAG_TOP_K)
                .query(query)
                .similarityThreshold(0.3F)
                .filterExpression(buildBaseAccessFilter(knowledgeBaseIds))
                .build();
        log.info("Vector Search Filter SQL: {}", searchRequest.getFilterExpression());
        log.info("Vector Search Filter Parameter: {}", knowledgeBaseIds);
        return QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(searchRequest)
                .build();
    }

    /**
     * 构造知识库访问过滤表达式。
     */
    public String buildBaseAccessFilter(List<String> knowledgeBaseIds) {
        // 如果没有 ID，返回一个 false 的表达式
        if (CollectionUtils.isEmpty(knowledgeBaseIds)) {
            return "knowledge_base_id in [\"___empty___\"]"; // 不让查询任何知识库
        }
        StringBuilder sb = new StringBuilder();
        sb.append("knowledge_base_id in [");
        for (int i = 0; i < knowledgeBaseIds.size(); i++) {
            if (i != 0) {
                sb.append(",");
            }
            sb.append("\"").append(knowledgeBaseIds.get(i)).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

}
