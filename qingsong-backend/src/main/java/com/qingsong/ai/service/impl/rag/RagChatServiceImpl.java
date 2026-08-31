package com.qingsong.ai.service.impl.rag;

import com.qingsong.ai.entity.exception.BusinessException;
import com.qingsong.ai.service.RagContextProvider;
import com.qingsong.ai.service.rag.RagChatService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * RAG 聊天服务实现
 *
 * @author : caojiangjiang
 * @since 2026/02/08
 */
@Service
public class RagChatServiceImpl implements RagChatService {

    private final ChatClient chatClient;
    private final RagContextProvider ragContextProvider;

    public RagChatServiceImpl(ChatClient.Builder builder, RagContextProvider ragContextProvider) {
        this.chatClient = builder.defaultSystem("你是一个专业的企业助手，请仅根据提供的上下文回答问题。").build();
        this.ragContextProvider = ragContextProvider;
    }

    @Override
    public String ask(String userQuery, List<String> knowledgeBaseIds) {
        if (CollectionUtils.isEmpty(knowledgeBaseIds)) {
            throw new BusinessException("请先指定知识库");
        }
        Advisor advisor = ragContextProvider.buildQuestionAnswerAdvisor(knowledgeBaseIds, userQuery);
        return chatClient.prompt()
                .user(userQuery)
                .advisors(advisor)
                .call()
                .content();
    }
}
