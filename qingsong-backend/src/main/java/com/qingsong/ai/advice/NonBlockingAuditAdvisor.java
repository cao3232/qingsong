package com.qingsong.ai.advice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
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
            return chain.nextStream(request)
                    .doOnComplete(recordElapsed)
                    .doOnError(error -> recordElapsed.run())
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
