package com.qingsong.ai.advice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * description...
 *
 * @author : caojiangjiang
 * @data : 2026/04/10 22:28
 */
@Slf4j
public class NonBlockingAuditAdvisor implements StreamAdvisor {

    public static final String ELAPSED_MS_CONTEXT_KEY = "elapsedMsHolder";
    public static final long NOT_COMPLETED = -1L;

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        Map<String, Object> context = request.context();
        Object holder = context == null ? null : context.get(ELAPSED_MS_CONTEXT_KEY);
        if (!(holder instanceof AtomicLong elapsedMs)) {
            return chain.nextStream(request);
        }
        return Flux.defer(() -> {
            long startedAt = System.nanoTime();
            AtomicBoolean finished = new AtomicBoolean();
            Runnable recordElapsed = () -> {
                if (finished.compareAndSet(false, true)) {
                    long elapsedNanos = Math.max(0L, System.nanoTime() - startedAt);
                    elapsedMs.set(elapsedNanos / 1_000_000L);
                }
            };

            // 响应式流的错误以 onError 信号异步传播（发生在订阅后的 Netty 线程上），
            // try-catch 只能捕获装配期的同步异常，对流内错误无效；
            // 必须用 doOnError 记录详情、onErrorMap 转换错误类型。
            return chain.nextStream(request)
                    .doOnComplete(recordElapsed)
                    .doOnError(error -> {
                        recordElapsed.run();
                        if (error instanceof WebClientResponseException wcre) {
                            log.error("AI {} 错误详情: {}", wcre.getStatusCode(), wcre.getResponseBodyAsString());
                        }
                    })
                    .onErrorMap(WebClientResponseException.class,
                            e -> new RuntimeException("AI 服务异常: " + e.getResponseBodyAsString(), e))
                    .doOnCancel(() -> {
                        recordElapsed.run();
                        log.debug("流订阅被下游取消");
                    });

        });
    }

    @Override
    public String getName() {
        return "non-blocking-audit";
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
