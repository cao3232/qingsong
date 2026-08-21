package com.qingsong.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import reactor.core.publisher.Mono;

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
