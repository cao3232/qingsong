package com.qingsong.ai.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.springframework.ai.content.Media;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Objects;

/**
 * AI 聊天请求上下文对象
 * 封装所有聊天相关的参数，提高代码可维护性和扩展性
 */
@Data
@Builder
@AllArgsConstructor
public class ChatRequest {

    /**
     * 系统提示词
     */
    private final String systemPrompt;

    /**
     * 用户提示词
     */
    private final String userPrompt;

    /**
     * 媒体文件列表（图片、音频等）
     */
    private final List<Media> medias;

    /**
     * 会话 ID（格式：role:chatId）
     */
    private final String conversationId;

    /**
     * 角色名称
     */
    private final String role;

    /**
     * 聊天 ID
     */
    private final String chatId;

    /**
     * 业务类型
     */
    private final String type;

    /**
     * 聊天选项配置（模型、温度等）
     */
        private final ToolCallingChatOptions chatOptions;

    /**
     * Redis 分布式锁
     */
    private final ChatLockHandle lock;

    /**
     * 知识库 id
     */
    private final List<String> kownledgeId;

    /**
     * 工具分组 key
     */
    private final List<String> toolGroupKey;


    /**
     * 是否重试
     */
    private final boolean retry;


    /**
     * 静态工厂方法 - 创建聊天请求
     *
     * @param system      系统提示词
     * @param promptStr   用户提示词
     * @param files       上传的文件列表
     * @param chatId      会话 ID
     * @param role        角色名称
     * @param kownledgeId
     * @param model       模型名称
     * @param lock        分布式锁
     * @param retry       是否重试
     * @return ChatRequest 对象
     */
    public static ChatRequest create(String system, String promptStr, List<MultipartFile> files,
                                     String chatId, String role, List<String> kownledgeId, String model,
                                      Double temperature, List<String> toolGroupKey, ChatLockHandle lock, boolean retry,
                                     String type) {
        return ChatRequest.builder()
                .systemPrompt(system)
                .userPrompt(promptStr)
                .medias(processMedias(files))
                .conversationId(role + ":" + chatId)
                .kownledgeId(kownledgeId)
                .role(role)
                .chatId(chatId)
                .type(type)
                .chatOptions(buildChatOptions(model, temperature))
                .toolGroupKey(toolGroupKey)
                .lock(lock)
                .retry(retry)
                .build();
    }

    /**
     * 处理媒体文件
     */
    private static List<Media> processMedias(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }

        return files.stream()
                .map(file -> new org.springframework.ai.content.Media(
                        org.springframework.util.MimeType.valueOf(
                                java.util.Objects.requireNonNull(file.getContentType())),
                        file.getResource()))
                .toList();
    }

    /**
     * 流式聊天默认最大输出 token。
     *
     * <p>⚠️ 特殊用法：设得足够大是为了避免<b>工具调用参数 JSON 在生成中被截断</b>
     * （模型需要先输出完整的工具参数，再进入工具循环）。</p>
     */
    private static final int DEFAULT_MAX_TOKENS = 16384;

    /**
     * 构建聊天选项
     */
    private static ToolCallingChatOptions buildChatOptions(String model, Double temperature) {
        ToolCallingChatOptions.Builder builder = ToolCallingChatOptions.builder();
        if (StringUtils.hasText(model)) {
            builder.model(model);
        }
        builder.maxTokens(DEFAULT_MAX_TOKENS);
        if(Objects.nonNull(temperature)) {
            builder.temperature(temperature);
        } else {
            builder.temperature(0.7);
        }
        return builder.build();
    }
}
