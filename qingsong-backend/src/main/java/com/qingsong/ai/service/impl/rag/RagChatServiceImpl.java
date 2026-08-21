package com.qingsong.ai.service.impl.rag;

import com.qingsong.ai.service.rag.RagChatService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

/**
 * RAG 聊天服务实现
 *
 * @author : caojiangjiang
 * @since 2026/02/08
 */
@Service
public class RagChatServiceImpl implements RagChatService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public RagChatServiceImpl(ChatClient.Builder builder, VectorStore pgVectorStore) {
        this.chatClient = builder.defaultSystem("你是一个专业的企业助手，请仅根据提供的上下文回答问题。").build();
        this.vectorStore = pgVectorStore;
    }

    @Override
    public String ask(String userQuery) {
        return chatClient.prompt()
                .user(userQuery)
                .advisors(QuestionAnswerAdvisor.builder(vectorStore)
                        .searchRequest(SearchRequest.builder().topK(3).similarityThreshold(0.7).build())
                        .build())
                .call()
                .content();
    }
}
