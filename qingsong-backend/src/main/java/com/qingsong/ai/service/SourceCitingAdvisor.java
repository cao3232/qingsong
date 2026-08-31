package com.qingsong.ai.service;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionTextParser;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 知识库问答 Advisor：检索向量库并把切片连同来源文件名一起渲染进上下文，让模型在回答中能引用出处。
 *
 * <p>替代 Spring AI 内置 {@code QuestionAnswerAdvisor}（其上下文渲染仅含切片正文），
 * 元数据中的 {@code file_name} 在此以「【来源：文件名】」形式随切片一并提供给模型。</p>
 */
public class SourceCitingAdvisor implements BaseAdvisor {

    /**
     * 上下文 key：本次检索命中的文档列表
     */
    public static final String RETRIEVED_DOCUMENTS_KEY = "qa_retrieved_documents";

    private static final String FILTER_EXPRESSION_KEY = "qa_filter_expression";

    private final VectorStore vectorStore;
    private final SearchRequest searchRequest;
    private final PromptTemplate promptTemplate;

    public SourceCitingAdvisor(VectorStore vectorStore, SearchRequest searchRequest, PromptTemplate promptTemplate) {
        this.vectorStore = vectorStore;
        this.searchRequest = searchRequest;
        this.promptTemplate = promptTemplate;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        String query = request.prompt().getUserMessage().getText();
        SearchRequest actualSearchRequest = SearchRequest.from(searchRequest)
                .query(query)
                .filterExpression(doGetFilterExpression(request.context()))
                .build();

        List<Document> retrievedDocuments = vectorStore.similaritySearch(actualSearchRequest);

        Map<String, Object> context = new HashMap<>(request.context());
        context.put(RETRIEVED_DOCUMENTS_KEY, retrievedDocuments);

        String contextText = retrievedDocuments.stream()
                .map(this::renderDocument)
                .collect(Collectors.joining(System.lineSeparator()));

        String augmentedUserMessage = promptTemplate.render(Map.of(
                "query", query,
                "question_answer_context", contextText));

        return request.mutate()
                .prompt(request.prompt().augmentUserMessage(augmentedUserMessage))
                .context(context)
                .build();
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        return response;
    }

    @Override
    public int getOrder() {
        return 0;
    }

    /**
     * 渲染单个切片：携带来源文件名
     */
    private String renderDocument(Document document) {
        String source = document.getMetadata().getOrDefault("file_name", "未知来源").toString();
        return "\n\n-----\n\n【来源：" + source + "】\n" + document.getText();
    }

    /**
     * 取过滤表达式：优先用上下文中的覆盖值，否则用检索配置
     */
    private Filter.Expression doGetFilterExpression(Map<String, Object> context) {
        if (context.containsKey(FILTER_EXPRESSION_KEY)
                && StringUtils.hasText(context.get(FILTER_EXPRESSION_KEY).toString())) {
            return new FilterExpressionTextParser().parse(context.get(FILTER_EXPRESSION_KEY).toString());
        }
        return searchRequest.getFilterExpression();
    }
}
