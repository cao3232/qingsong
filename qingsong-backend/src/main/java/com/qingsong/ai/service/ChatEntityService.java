package com.qingsong.ai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.stereotype.Service;

/**
 * 非流式实体对话服务（内部用例专用）。
 *
 * <p>与流式主聊天（{@link ChatService#executeStreamingChat}）用例不同：
 * 这里直接调用模型并把结果映射为强类型实体（如代码片段自动打标/生成），
 * 不落库、不走 SSE、不涉及工具。复用 {@link ChatService} 的请求校验与
 * Prompt 构建（同包静态方法）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatEntityService {

    private final ModelConfigService modelConfigService;

    /**
     * 执行非流式实体对话（带 280s 超时，略小于 Netty 的 300 秒）。
     */
    public <T> T executeChatWithEntity(ChatRequest request, ChatClient userChatClient, Class<T> entity) {
        try {
            ChatService.validateRequest(request);
            return ChatService.buildChatPrompt(userChatClient, request)
                    .system(request.getSystemPrompt())
                    .options(ChatOptions.builder()
                            .model(getInnerModel())
                            .maxTokens(8000)
                            .build())
                    .call()
                    .entity(entity);
        } catch (io.netty.handler.timeout.ReadTimeoutException e) {
            log.error("AI 请求超时，请检查网络连接或稍后重试: {}", e.getMessage(), e);
            throw new RuntimeException("AI 服务响应超时，请稍后重试。如果问题持续存在，请联系管理员", e);
        } catch (Exception e) {
            log.error("executeChatWithEntity error:{}", e.getMessage(), e);
            throw new RuntimeException("AI 服务调用失败: " + e.getMessage(), e);
        }
    }

    private String getInnerModel() {
        return modelConfigService.getInnerModel();
    }

}
