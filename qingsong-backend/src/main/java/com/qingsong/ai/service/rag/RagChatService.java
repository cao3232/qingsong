package com.qingsong.ai.service.rag;

import java.util.List;

/**
 * RAG 聊天服务接口
 *
 * @author : caojiangjiang
 * @since 2026/02/08
 */
public interface RagChatService {

    /**
     * 当用户指定知识库发起问答时，仅基于指定知识库检索上下文并生成回答。
     *
     * @param userQuery        用户问题
     * @param knowledgeBaseIds 允许访问的知识库 ID（非空）
     * @return AI 回答
     */
    String ask(String userQuery, List<String> knowledgeBaseIds);
}
