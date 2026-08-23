package com.qingsong.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * 工具回调观察包装器（IoC：执行边界拦截）。
 *
 * <p>把底层 {@link ToolCallback} 的每次调用转化为工具执行事件，写入注入的事件总线：
 * 调用前发布 {@code tool_call}（本地生成的 toolCallId），执行完成后发布
 * {@code tool_result}（success / failed）。工具抛出异常时发布 {@code failed} 后
 * <b>原样重新抛出</b>，保留 Spring AI {@code ToolExecutionExceptionProcessor}
 * 的模型自愈语义（错误回传给模型，而不是中断整个流）。</p>
 *
 * <p>展示用的 {@code args}/{@code result} 会截断到 {@link #TOOL_RESULT_MAX_CHARS}，
 * 不影响传给底层工具/模型的实际值。</p>
 */
@Slf4j
public class ObservingToolCallback implements ToolCallback {

    public static final int TOOL_RESULT_MAX_CHARS = 2000;

    private final ToolCallback delegate;
    private final ToolExecutionEventBus eventBus;

    public ObservingToolCallback(ToolCallback delegate, ToolExecutionEventBus eventBus) {
        this.delegate = delegate;
        this.eventBus = eventBus;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        String toolCallId = UUID.randomUUID().toString();
        String name = delegate.getToolDefinition().name();
        eventBus.emitToolCall(toolCallId, name, truncate(toolInput));
        long startedAt = System.nanoTime();
        try {
            String result = delegate.call(toolInput);
            eventBus.emitToolResult(toolCallId, name, "success", truncate(result), null, elapsedMs(startedAt));
            return result;
        } catch (Exception e) {
            log.warn("工具执行失败, name={}, error={}", name, e.getMessage());
            eventBus.emitToolResult(toolCallId, name, "failed", null,
                    StringUtils.hasText(e.getMessage()) ? e.getMessage() : e.getClass().getSimpleName(),
                    elapsedMs(startedAt));
            throw e;
        }
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        // 与 call(String) 走同一观测路径
        return call(toolInput);
    }

    private static long elapsedMs(long startedAtNanos) {
        return Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L);
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= TOOL_RESULT_MAX_CHARS
                ? value
                : value.substring(0, TOOL_RESULT_MAX_CHARS) + "…(已截断)";
    }

}
