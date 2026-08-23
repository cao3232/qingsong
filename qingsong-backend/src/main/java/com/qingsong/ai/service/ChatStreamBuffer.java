package com.qingsong.ai.service;

import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 流式正文缓冲器。
 *
 * <p>把过碎的模型增量合并成稍大的块再下发，减少 SSE 事件数与网络开销，
 * 同时保证交互的实时性。特殊用法说明：</p>
 * <ul>
 *   <li>类型切换（reasoning ↔ content）会<b>立即刷出</b>缓冲区，避免两种内容互相混入；</li>
 *   <li>{@code usage} / {@code elapsedMs} / 工具事件等元数据 part <b>立即透传</b>，不参与正文缓冲，
 *       因此它们永远不会被延迟到下一个正文块之后；</li>
 *   <li>上游结束时通过 {@code concatWith} 补排一次，把积压在缓冲区里的剩余字符刷干净。</li>
 * </ul>
 */
public final class ChatStreamBuffer {

    public static final int STREAM_BATCH_MAX_CHARS = 48;
    public static final Duration STREAM_BATCH_FLUSH_INTERVAL = Duration.ofMillis(50);

    private ChatStreamBuffer() {
    }

    /** 合并过碎的同类增量，类型切换和 usage 元数据会立即刷出。 */
    public static Flux<ChatStreamPart> bufferChatParts(Flux<ChatStreamPart> source,
                                                       int maxChars,
                                                       Duration flushInterval) {
        if (maxChars <= 0 || flushInterval == null || flushInterval.isNegative() || flushInterval.isZero()) {
            throw new IllegalArgumentException("invalid stream buffer settings");
        }
        return Flux.defer(() -> {
            StringBuilder content = new StringBuilder();
            StringBuilder reasoning = new StringBuilder();
            AtomicReference<String> type = new AtomicReference<>();
            return source
                    .bufferTimeout(maxChars, flushInterval)
                    .concatMap(batch -> {
                        List<ChatStreamPart> output = new ArrayList<>();
                        ChatStreamPart.TokenUsage latestUsage = null;
                        Long latestElapsedMs = null;
                        for (ChatStreamPart part : batch) {
                            if (part == null) continue;

                            // 工具事件：立即透传，不参与正文缓冲（与 usage/elapsed 相同）
                            if (part.tool() != null) {
                                output.add(part);
                                continue;
                            }

                            if (part.usage() != null) {
                                latestUsage = part.usage();
                            }
                            if (part.elapsedMs() != null) {
                                latestElapsedMs = part.elapsedMs();
                            }

                            if (part.reasoningContent() != null && !part.reasoningContent().isEmpty()) {
                                if (type.get() != null && !"reasoning".equals(type.get())) {
                                    flushBuffers(output, content, reasoning, type);
                                }
                                type.set("reasoning");
                                reasoning.append(part.reasoningContent());
                            }
                            if (part.content() != null && !part.content().isEmpty()) {
                                if (type.get() != null && !"content".equals(type.get())) {
                                    flushBuffers(output, content, reasoning, type);
                                }
                                type.set("content");
                                content.append(part.content());
                            }

                            StringBuilder target = "reasoning".equals(type.get()) ? reasoning : content;
                            while (target.length() >= maxChars) {
                                String chunk = target.substring(0, maxChars);
                                target.delete(0, maxChars);
                                output.add("reasoning".equals(type.get())
                                        ? ChatStreamPart.reasoning(chunk) : ChatStreamPart.content(chunk));
                            }
                        }
                        flushBuffers(output, content, reasoning, type);
                        if (latestUsage != null) {
                            output.add(ChatStreamPart.usage(latestUsage));
                        }
                        if (latestElapsedMs != null) {
                            output.add(ChatStreamPart.elapsed(latestElapsedMs));
                        }
                        return Flux.fromIterable(output);
                    })
                    .concatWith(Flux.defer(() -> {
                        List<ChatStreamPart> tail = new ArrayList<>();
                        flushBuffers(tail, content, reasoning, type);
                        return Flux.fromIterable(tail);
                    }));
        });
    }

    private static void flushBuffers(List<ChatStreamPart> output,
                                     StringBuilder content,
                                     StringBuilder reasoning,
                                     AtomicReference<String> type) {
        if (reasoning.length() > 0) {
            output.add(ChatStreamPart.reasoning(reasoning.toString()));
            reasoning.setLength(0);
        }
        if (content.length() > 0) {
            output.add(ChatStreamPart.content(content.toString()));
            content.setLength(0);
        }
        type.set(null);
    }

}
