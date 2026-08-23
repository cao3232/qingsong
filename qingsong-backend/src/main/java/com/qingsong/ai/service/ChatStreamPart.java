package com.qingsong.ai.service;

import java.util.Objects;

/**
 * 一个模型流增量，正文、推理、用量、工具事件彼此独立。
 *
 * <p>工具执行观测：{@code tool} 字段非空时表示一条工具执行事件
 * （{@code toolCall} 开始或 {@code toolResult} 结束），
 * 由 {@link ObservingToolCallback} 写入、经 {@link ToolExecutionEventBus} 注入流。</p>
 */
public record ChatStreamPart(String content, String reasoningContent, TokenUsage usage, Long elapsedMs, ToolExecution tool) {

    public ChatStreamPart(String content, String reasoningContent, TokenUsage usage) {
        this(content, reasoningContent, usage, null, null);
    }

    public ChatStreamPart(String content, String reasoningContent, TokenUsage usage, Long elapsedMs) {
        this(content, reasoningContent, usage, elapsedMs, null);
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

    /** 工具调用开始事件（本地生成 toolCallId 用于与结果配对）。 */
    public static ChatStreamPart toolCall(ToolExecution tool) {
        return new ChatStreamPart(null, null, null, null, tool);
    }

    /** 工具执行结束事件（success / failed）。 */
    public static ChatStreamPart toolResult(ToolExecution tool) {
        return new ChatStreamPart(null, null, null, null, tool);
    }

    /**
     * 工具执行事件。
     *
     * @param type       "call"（开始）或 "result"（结束）
     * @param toolCallId 本地生成，用于 tool_call ↔ tool_result 配对
     * @param name       工具名
     * @param args       调用参数 JSON（展示用，可能截断）
     * @param status     result 时：success / failed
     * @param result     工具返回结果（展示用，可能截断）
     * @param error      失败原因
     * @param durationMs 执行耗时
     */
    public record ToolExecution(String type, String toolCallId, String name, String args,
                                String status, String result, String error, Long durationMs) {

        public ToolExecution {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(toolCallId, "toolCallId");
            Objects.requireNonNull(name, "name");
        }

        public boolean isCall() {
            return "call".equals(type);
        }

        public boolean isResult() {
            return "result".equals(type);
        }

        public boolean isFailed() {
            return isResult() && "failed".equals(status);
        }
    }

    public record TokenUsage(Long promptTokens, Long completionTokens, Long totalTokens) {
        public TokenUsage {
            Objects.requireNonNull(promptTokens, "promptTokens");
            Objects.requireNonNull(completionTokens, "completionTokens");
            Objects.requireNonNull(totalTokens, "totalTokens");
        }
    }
}
