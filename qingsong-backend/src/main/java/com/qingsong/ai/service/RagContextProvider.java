package com.qingsong.ai.service;

import com.qingsong.ai.config.RagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.prompt.PromptTemplate;
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
 *
 * <p>检索参数（topK / 相似度阈值）来自 {@link RagProperties}，可在 application.yaml 的
 * {@code qingsong.rag.*} 调优。问答 Advisor 使用 {@link SourceCitingAdvisor}，切片带来源文件名渲染。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagContextProvider {

    /**
     * 默认 topK（兼容旧引用）
     */
    public static final int RAG_TOP_K = 5;

    private static final String RAG_PROMPT_TEMPLATE =
            "{query}\n\n" +
                    "以下是知识库检索到的相关上下文（每条前面标注了来源文件名）：\n\n" +
                    "---------------------\n" +
                    "{question_answer_context}\n" +
                    "---------------------\n\n" +
                    "请仅依据上方上下文回答用户的问题。如果上下文中没有足够信息，请明确告知「知识库中没有相关信息」，" +
                    "不要编造或猜测。回答使用中文，必要时分点说明；如引用了某份资料的内容，请注明来源文件名（如「来源：xxx」）。";

    private final VectorStore vectorStore;
    private final RagProperties ragProperties;

    /**
     * 构建知识库问答 Advisor。
     *
     * @param knowledgeBaseIds 允许访问的知识库 ID（调用方保证非空）
     * @param query            检索 query（通常为当前用户问题）
     */
    public Advisor buildQuestionAnswerAdvisor(List<String> knowledgeBaseIds, String query) {
        SearchRequest searchRequest = SearchRequest.builder()
                .topK(ragProperties.getTopK())
                .query(query)
                .similarityThreshold(ragProperties.getSimilarityThreshold())
                .filterExpression(buildBaseAccessFilter(knowledgeBaseIds))
                .build();
        log.info("Vector Search Filter SQL: {}", searchRequest.getFilterExpression());
        log.info("Vector Search Filter Parameter: {}", knowledgeBaseIds);
        return new SourceCitingAdvisor(vectorStore, searchRequest, new PromptTemplate(RAG_PROMPT_TEMPLATE));
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
