package com.qingsong.ai.service;

import java.util.Objects;

/** 一个模型流增量，正文、推理和最终用量彼此独立。 */
public record ChatStreamPart(String content, String reasoningContent, TokenUsage usage, Long elapsedMs) {

    public ChatStreamPart(String content, String reasoningContent, TokenUsage usage) {
        this(content, reasoningContent, usage, null);
    }

    public static ChatStreamPart content(String content) {
        return new ChatStreamPart(content, null, null);
    }

    public static ChatStreamPart reasoning(String reasoningContent) {
        return new ChatStreamPart(null, reasoningContent, null);
    }

    public static ChatStreamPart usage(TokenUsage usage) {
        return new ChatStreamPart(null, null, usage);
    }

    public static ChatStreamPart elapsed(long elapsedMs) {
        if (elapsedMs < 0) {
            throw new IllegalArgumentException("elapsedMs must not be negative");
        }
        return new ChatStreamPart(null, null, null, elapsedMs);
    }

    public record TokenUsage(Long promptTokens, Long completionTokens, Long totalTokens) {
        public TokenUsage {
            Objects.requireNonNull(promptTokens, "promptTokens");
            Objects.requireNonNull(completionTokens, "completionTokens");
            Objects.requireNonNull(totalTokens, "totalTokens");
        }
    }
}
