package com.qingsong.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import reactor.core.publisher.Mono;

/**
 * 会话分布式锁句柄。
 *
 * <p>关键点：流式响应在 Reactor 线程完成，而锁在请求线程（或 CompletableFuture 线程）获取，
 * Redisson 按线程记录持有者，因此释放时必须用 {@code unlockAsync(ownerThreadId)}
 * 显式指定获取时的 owner 线程 ID，否则会因"非持有线程解锁"失败。</p>
 */
@Slf4j
public final class ChatLockHandle {

    private final RLock lock;
    private final long ownerThreadId;

    public ChatLockHandle(RLock lock, long ownerThreadId) {
        this.lock = lock;
        this.ownerThreadId = ownerThreadId;
    }

    public Mono<Void> release() {
        if (lock == null) {
            return Mono.empty();
        }
        return Mono.defer(() -> {
            log.debug("释放会话锁, ownerThreadId={}", ownerThreadId);
            return Mono.fromCompletionStage(
                    lock.unlockAsync(ownerThreadId).toCompletableFuture());
        });
    }
}
