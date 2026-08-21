package com.qingsong.ai.service.rag;

/**
 * RAG 聊天服务接口
 *
 * @author : caojiangjiang
 * @since 2026/02/08
 */
public interface RagChatService {

    /**
     * 问答查询
     *
     * @param userQuery 用户问题
     * @return AI 回答
     */
    String ask(String userQuery);
}
