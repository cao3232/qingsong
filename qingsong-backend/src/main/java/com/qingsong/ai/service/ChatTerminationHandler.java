package com.qingsong.ai.service;

import com.qingsong.ai.service.chat.ChatPersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.core.publisher.Sinks;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

/**
 * 聊天流收尾处理器。
 *
 * <p>负责流式响应的终止阶段：累积正文、持久化助手消息、释放会话锁，并保证
 * 正常/异常/取消三种终止路径下「持久化 + 解锁」最多执行一次。</p>
 *
 * <h3>特殊用法（不要改动时序）：</h3>
 * <ul>
 *   <li>{@link #accumulateChatParts} 用 {@code materialize/concatMap/dematerialize}
 *       拦截 ON_COMPLETE/ON_ERROR 信号，<b>收尾完成后下游才看到终止信号</b>，
 *       保证消息已落库、锁已释放再结束；</li>
 *   <li>{@code Sinks.One} + fire-and-forget 订阅：收尾作为独立订阅运行，<b>不受下游取消影响</b>
 *       （下游取消只取消对结果的等待，不取消收尾本身）；</li>
 *   <li>{@code AtomicBoolean terminated} 保证 {@code ON_COMPLETE / ON_ERROR / CANCEL}
 *       三者只有一个能触发收尾；</li>
 *   <li>ON_ERROR 路径收尾失败不掩盖原始模型异常，仅记日志；</li>
 *   <li>CANCEL 路径持久化与解锁<b>并行</b>执行，锁尽快释放，避免取消后重试请求被
 *       「正在思考中」拦截。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatTerminationHandler {

    private final ChatPersistenceService chatPersistenceService;

    /**
     * 处理流终止收尾（持久化 + 释放锁）。
     *
     * <p>ON_COMPLETE：先持久化，持久化失败也释放锁再抛出异常，保证锁在任何情况下释放；
     * ON_ERROR/CANCEL：持久化与释放均为 best-effort，失败只记日志。</p>
     */
    public Mono<Void> completeTermination(ChatRequest request, SignalType signalType, String content) {
        Mono<Void> persist = Mono.<Void>fromRunnable(() ->
                        persistAssistantMessage(request, signalType.toString(), content))
                .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
        Mono<Void> release = safeReleaseLock(request.getLock());
        if (signalType == SignalType.ON_COMPLETE) {
            // 持久化失败 → 释放锁后重新抛出持久化异常；持久化成功 → 释放锁
            return persist
                    .onErrorResume(error -> release.then(Mono.error(error)))
                    .then(release);
        }
        // ON_ERROR / CANCEL：持久化与释放锁并行执行，锁尽快释放
        Mono<Void> persistBestEffort = persist.onErrorResume(error -> {
            log.error("聊天终止持久化失败, signalType={}", signalType, error);
            return Mono.empty();
        });
        Mono<Void> releaseBestEffort = release.onErrorResume(error -> {
            log.error("聊天锁异步释放失败, signalType={}", signalType, error);
            return Mono.empty();
        });
        return Mono.when(persistBestEffort, releaseBestEffort).then();
    }

    /**
     * 用户主动取消时回调（当前为占位，行为由日志与收尾逻辑保证）。
     */
    public void handleCancel(String chatId) {
        log.info("用户主动取消对话 chatId:{}", chatId);
    }

    /**
     * 累积流式内容并在流终止时执行一次性收尾回调。
     *
     * @param source      原始流式响应
     * @param onTerminate 收尾回调，接收（信号类型, 累积内容），返回收尾 Mono
     */
    static Flux<ChatStreamPart> accumulateChatParts(Flux<ChatStreamPart> source,
                                                    BiFunction<SignalType, String, Mono<Void>> onTerminate) {
        return Flux.defer(() -> {
            StringBuilder contentAccumulator = new StringBuilder();
            AtomicBoolean terminated = new AtomicBoolean();
            Sinks.One<Void> cleanupResult = Sinks.one();
            BiFunction<SignalType, String, Mono<Void>> startCleanup = (signalType, content) -> {
                if (terminated.compareAndSet(false, true)) {
                    onTerminate.apply(signalType, content).subscribe(
                            ignored -> { },
                            cleanupResult::tryEmitError,
                            cleanupResult::tryEmitEmpty);
                }
                return cleanupResult.asMono();
            };
            return source
                    .doOnNext(part -> {
                        if (part != null && part.content() != null) contentAccumulator.append(part.content());
                    })
                    .materialize()
                    .concatMap(signal -> {
                        if (signal.getType() == SignalType.ON_COMPLETE || signal.getType() == SignalType.ON_ERROR) {
                            Mono<Void> cleanup = startCleanup.apply(signal.getType(), contentAccumulator.toString());
                            if (signal.getType() == SignalType.ON_ERROR) {
                                cleanup = cleanup.onErrorResume(error -> {
                                    log.error("聊天异常收尾失败，保留原始异常", error);
                                    return Mono.empty();
                                });
                            }
                            return cleanup.thenReturn(signal);
                        }
                        return Mono.just(signal);
                    })
                    .<ChatStreamPart>dematerialize()
                    .doOnCancel(() -> startCleanup.apply(SignalType.CANCEL, contentAccumulator.toString())
                            .subscribe(ignored -> { }, error -> log.error("聊天取消异步清理失败", error)));
        });
    }

    private Mono<Void> safeReleaseLock(ChatLockHandle lock) {
        return lock == null ? Mono.empty() : lock.release();
    }

    private void persistAssistantMessage(ChatRequest request, String signalType, String content) {
        String persistedContent = content;
        if ((persistedContent == null || persistedContent.isBlank()) && "onError".equalsIgnoreCase(signalType)) {
            persistedContent = buildFriendlyErrorMessage(null);
        }
        if (persistedContent == null || persistedContent.isBlank()) {
            return;
        }

        chatPersistenceService.appendAssistantMessage(
                request.getType(),
                request.getRole(),
                request.getChatId(),
                persistedContent,
                signalType,
                request.getChatOptions().getModel()
        );
    }

    private String buildFriendlyErrorMessage(String message) {
        return "⚠️错误信息: " + message;
    }

}
