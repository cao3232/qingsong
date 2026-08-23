package com.qingsong.ai.service;

import reactor.core.publisher.Flux;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

/**
 * 工具执行事件总线（每请求一个实例）。
 *
 * <p>由 {@link ObservingToolCallback} 通过构造器注入写入，主聊天流在
 * 发射每个 part 前排空（保证工具事件先于后续正文），流结束时补排。</p>
 *
 * <p>实现采用线程安全队列而非 {@code Sinks.Many}：工具执行发生在 Spring AI
 * 自动 ToolCallingAdvisor 循环内（与流消费可能不同线程），需要「按需取走已累积事件」
 * 的语义，{@code Sinks.Many} 的持续订阅模型无法暂停/恢复。</p>
 */
public final class ToolExecutionEventBus {

    private final Queue<ChatStreamPart> buffer = new ArrayDeque<>();

    public void emitToolCall(String toolCallId, String name, String args) {
        buffer.add(ChatStreamPart.toolCall(new ChatStreamPart.ToolExecution(
                "call", toolCallId, name, args, null, null, null, null)));
    }

    public void emitToolResult(String toolCallId, String name, String status, String result,
                               String error, Long durationMs) {
        buffer.add(ChatStreamPart.toolResult(new ChatStreamPart.ToolExecution(
                "result", toolCallId, name, null, status, result, error, durationMs)));
    }

    /**
     * 取走并清空当前累积的工具事件。
     *
     * @return 已累积事件的有序流（无事件时为空流）
     */
    public Flux<ChatStreamPart> drain() {
        List<ChatStreamPart> events = new ArrayList<>(buffer.size());
        ChatStreamPart event;
        while ((event = buffer.poll()) != null) {
            events.add(event);
        }
        return Flux.fromIterable(events);
    }

    /**
     * 是否还有未排空的事件（测试辅助）。
     */
    public boolean hasPending() {
        return !buffer.isEmpty();
    }

}
