package com.qingsong.ai.controller.chat;

import com.qingsong.ai.entity.exception.BusinessException;
import com.qingsong.ai.service.ChatStreamPart;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Component
@Slf4j
public class ChatSseEventMapper {

    private static final String ERROR_CODE = "CHAT_STREAM_ERROR";
    private static final String UNKNOWN_ERROR_MESSAGE = "生成失败，请稍后重试";

    public Flux<ServerSentEvent<Map<String, Object>>> mapParts(
            Flux<ChatStreamPart> parts, String chatId, String requestId) {
        return Flux.defer(() -> {
            AtomicLong sequence = new AtomicLong();
            AtomicReference<ChatStreamPart.TokenUsage> usage = new AtomicReference<>();
            AtomicReference<Long> elapsedMs = new AtomicReference<>();
            return Flux.concat(
                            Flux.just(event("meta", meta(chatId, requestId))),
                            parts.doOnNext(part -> {
                                         if (part != null && part.usage() != null) usage.set(part.usage());
                                         if (part != null && part.elapsedMs() != null) elapsedMs.set(part.elapsedMs());
                                     })
                                    .concatMap(part -> mapPart(part, requestId, sequence)),
                            Flux.defer(() -> Flux.just(event("done", done(chatId, requestId, sequence.incrementAndGet(), usage.get(), elapsedMs.get()))))
                    )
                    .onErrorResume(error -> Flux.just(event("error", error(error, chatId, requestId, sequence.incrementAndGet()))));
        });
    }

    public Flux<ServerSentEvent<Map<String, String>>> map(Flux<String> chunks, String chatId) {
        return chunks.filter(StringUtils::hasText)
                .map(chunk -> ServerSentEvent.<Map<String, String>>builder()
                        .event("chunk").data(Map.of("content", chunk)).build())
                .concatWith(Flux.just(ServerSentEvent.<Map<String, String>>builder()
                        .event("done").data(Map.of("chatId", chatId, "finishReason", "completed")).build()))
                .onErrorResume(error -> Flux.just(ServerSentEvent.<Map<String, String>>builder()
                        .event("error").data(Map.of("code", ERROR_CODE, "message", safeMessage(error))).build()));
    }

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

    private Map<String, Object> done(String chatId, String requestId, long sequence,
                                     ChatStreamPart.TokenUsage usage, Long elapsedMs) {
        Map<String, Object> data = base(requestId, sequence);
        data.put("chatId", chatId);
        data.put("finishReason", "completed");
        if (usage != null) {
            data.put("usage", usage);
        }
        if (elapsedMs != null) {
            data.put("elapsedMs", elapsedMs);
        }
        return data;
    }

    private Map<String, Object> error(Throwable error, String chatId, String requestId, long sequence) {
        String message = safeMessage(error);
        if (!(error instanceof BusinessException && StringUtils.hasText(error.getMessage()))) {
            log.error("聊天流生成失败, chatId={}", chatId, error);
        }
        Map<String, Object> data = base(requestId, sequence);
        data.put("code", ERROR_CODE);
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
