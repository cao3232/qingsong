package com.qingsong.ai.controller.chat;

import com.qingsong.ai.entity.exception.BusinessException;
import com.qingsong.ai.service.ChatStreamPart;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ChatStreamPart → SSE 事件映射器。
 *
 * <p>事件类型：{@code meta / reasoning / chunk / tool_call / tool_result / done / error}。</p>
 * <ul>
 *   <li>sequence 严格连续递增（meta 为 0），前端解析器要求连续，不得跳号；</li>
 *   <li>除 meta 外事件均携带与 meta 一致的 requestId；</li>
 *   <li>done 事件附带 {@code tools} 汇总（按 toolCallId 合并的本次工具执行序列）。</li>
 * </ul>
 */
@Component
@Slf4j
public class ChatSseEventMapper {

    private static final String ERROR_CODE = "CHAT_STREAM_ERROR";
    private static final String UNKNOWN_ERROR_MESSAGE = "生成失败，请稍后重试";
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(3);

    public Flux<ServerSentEvent<Map<String, Object>>> mapParts(
            Flux<ChatStreamPart> parts, String chatId, String requestId) {
        return Flux.defer(() -> {
            AtomicLong sequence = new AtomicLong();
            AtomicReference<ChatStreamPart.TokenUsage> usage = new AtomicReference<>();
            AtomicReference<Long> elapsedMs = new AtomicReference<>();
            List<ChatStreamPart.ToolExecution> tools = new ArrayList<>();
            Flux<ServerSentEvent<Map<String, Object>>> dataEvents = parts.doOnNext(part -> {
                        if (part == null) return;
                        if (part.usage() != null) usage.set(part.usage());
                        if (part.elapsedMs() != null) elapsedMs.set(part.elapsedMs());
                        if (part.tool() != null) tools.add(part.tool());
                    })
                    .concatMap(part -> mapPart(part, requestId, sequence));
            // publish(Function)：单一订阅共享给心跳，避免对 parts 二次订阅导致模型被重复调用；
            // 心跳为 `:` 注释行，前端 SSE 解析器会忽略，不影响事件顺序与 sequence。
            return dataEvents.publish(shared -> Flux.concat(
                            Flux.just(event("meta", meta(chatId, requestId))),
                            Flux.merge(
                                    shared,
                                    heartbeat().takeUntilOther(shared.ignoreElements())
                            ),
                            Flux.defer(() -> Flux.just(event("done", done(chatId, requestId,
                                    sequence.incrementAndGet(), usage.get(), elapsedMs.get(), tools))))
                    ))
                    .onErrorResume(error -> Flux.just(event("error", error(error, chatId, requestId, sequence.incrementAndGet()))));
        });
    }

    /**
     * SSE 注释心跳：客户端断开后，下一次心跳写入失败会使订阅被取消，
     * 从而触发 ChatService 的收尾逻辑释放会话锁，避免取消后锁一直不释放。
     */
    private Flux<ServerSentEvent<Map<String, Object>>> heartbeat() {
        return Flux.interval(HEARTBEAT_INTERVAL, HEARTBEAT_INTERVAL)
                .map(seq -> ServerSentEvent.<Map<String, Object>>builder().comment("ping").build())
                .onErrorResume(error -> Flux.empty());
    }

    /**
     * 供测试/内部使用的字符串流入口（最终复用 mapParts）。
     */
    public Flux<ServerSentEvent<Map<String, Object>>> map(Flux<String> chunks, String chatId, String requestId) {
        return mapParts(chunks.map(ChatStreamPart::content), chatId, requestId);
    }

    private Flux<ServerSentEvent<Map<String, Object>>> mapPart(ChatStreamPart part, String requestId, AtomicLong sequence) {
        if (part == null) {
            return Flux.empty();
        }
        List<ServerSentEvent<Map<String, Object>>> events = new ArrayList<>(2);
        if (StringUtils.hasText(part.reasoningContent())) {
            events.add(event("reasoning", payload(requestId, sequence.incrementAndGet(), part.reasoningContent())));
        }
        if (part.content() != null && !part.content().isEmpty()) {
            events.add(event("chunk", payload(requestId, sequence.incrementAndGet(), part.content())));
        }
        if (part.tool() != null) {
            // tool_call / tool_result 走同一 sequence，保证前端 sequence 连续
            String eventName = part.tool().isCall() ? "tool_call" : "tool_result";
            events.add(event(eventName, toolPayload(requestId, sequence.incrementAndGet(), part.tool())));
        }
        return Flux.fromIterable(events);
    }

    private Map<String, Object> meta(String chatId, String requestId) {
        Map<String, Object> data = base(requestId, 0L);
        data.put("chatId", chatId);
        data.put("protocolVersion", 1);
        data.put("sequence", 0L);
        return data;
    }

    private Map<String, Object> payload(String requestId, long sequence, String content) {
        Map<String, Object> data = base(requestId, sequence);
        data.put("content", content);
        return data;
    }

    private Map<String, Object> toolPayload(String requestId, long sequence, ChatStreamPart.ToolExecution tool) {
        Map<String, Object> data = base(requestId, sequence);
        data.put("toolCallId", tool.toolCallId());
        data.put("name", tool.name());
        if (tool.isCall()) {
            data.put("status", "running");
            if (tool.args() != null) {
                data.put("args", tool.args());
            }
        } else {
            data.put("status", tool.status());
            if (tool.result() != null) {
                data.put("result", tool.result());
            }
            if (tool.error() != null) {
                data.put("error", tool.error());
            }
            if (tool.durationMs() != null) {
                data.put("durationMs", tool.durationMs());
            }
        }
        return data;
    }

    private Map<String, Object> done(String chatId, String requestId, long sequence,
                                     ChatStreamPart.TokenUsage usage, Long elapsedMs,
                                     List<ChatStreamPart.ToolExecution> tools) {
        Map<String, Object> data = base(requestId, sequence);
        data.put("chatId", chatId);
        data.put("finishReason", "completed");
        if (usage != null) {
            data.put("usage", usage);
        }
        if (elapsedMs != null) {
            data.put("elapsedMs", elapsedMs);
        }
        List<Map<String, Object>> toolsSummary = buildToolsSummary(tools);
        if (!toolsSummary.isEmpty()) {
            data.put("tools", toolsSummary);
        }
        return data;
    }

    /**
     * 按 toolCallId 合并本次工具执行序列：call 提供 args，result 回填 status/result/error/durationMs。
     */
    private List<Map<String, Object>> buildToolsSummary(List<ChatStreamPart.ToolExecution> tools) {
        if (tools == null || tools.isEmpty()) {
            return List.of();
        }
        Map<String, Map<String, Object>> merged = new LinkedHashMap<>();
        for (ChatStreamPart.ToolExecution tool : tools) {
            Map<String, Object> entry = merged.computeIfAbsent(tool.toolCallId(), id -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("toolCallId", id);
                m.put("name", tool.name());
                return m;
            });
            if (tool.isCall()) {
                if (tool.args() != null) {
                    entry.put("args", tool.args());
                }
                entry.putIfAbsent("status", "running");
            } else {
                entry.put("status", tool.status());
                if (tool.result() != null) {
                    entry.put("result", tool.result());
                }
                if (tool.error() != null) {
                    entry.put("error", tool.error());
                }
                if (tool.durationMs() != null) {
                    entry.put("durationMs", tool.durationMs());
                }
            }
        }
        return new ArrayList<>(merged.values());
    }

    private Map<String, Object> error(Throwable error, String chatId, String requestId, long sequence) {
        String message = safeMessage(error);
        if (!(error instanceof BusinessException && StringUtils.hasText(error.getMessage()))) {
            log.error("聊天流生成失败, chatId={}", chatId, error);
        }
        Map<String, Object> data = base(requestId, sequence);
        data.put("code", error instanceof BusinessException && StringUtils.hasText(((BusinessException) error).getCode())
                ? ((BusinessException) error).getCode() : ERROR_CODE);
        data.put("message", message);
        return data;
    }

    private String safeMessage(Throwable error) {
        return error instanceof BusinessException && StringUtils.hasText(error.getMessage())
                ? error.getMessage() : UNKNOWN_ERROR_MESSAGE;
    }


    private Map<String, Object> base(String requestId, long sequence) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("protocolVersion", 1);
        if (requestId != null) data.put("requestId", requestId);
        if (sequence > 0) data.put("sequence", sequence);
        return data;
    }

    private ServerSentEvent<Map<String, Object>> event(String name, Map<String, Object> data) {
        return ServerSentEvent.<Map<String, Object>>builder().event(name).data(data).build();
    }
}
