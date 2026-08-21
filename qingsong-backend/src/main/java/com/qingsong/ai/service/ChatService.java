package com.qingsong.ai.service;

import com.qingsong.ai.advice.NonBlockingAuditAdvisor;
import com.qingsong.ai.aspect.MyToolAnnotationAspect;
import com.qingsong.ai.service.chat.ChatPersistenceService;
import com.qingsong.ai.tools.AITools;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.content.Media;
import org.springframework.ai.mcp.AsyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * AI 聊天服务类
 * 负责处理所有与 AI 聊天相关的业务逻辑
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private static final int RAG_TOP_K = 5;
    private static final int STREAM_BATCH_MAX_CHARS = 48;
    private static final Duration STREAM_BATCH_FLUSH_INTERVAL = Duration.ofMillis(50);
    private final AsyncMcpToolCallbackProvider mcpToolCallbackProvider;
    private final VectorStore vectorStore;
    private final ModelConfigService modelConfigService;
    private final ChatPersistenceService chatPersistenceService;

    private final AITools aiTools;
    /**
     * 执行流式 AI 对话
     *
     * @param request        聊天请求上下文
     * @param userChatClient ChatClient 实例
     * @return Flux<String> 流式响应
     */
    public Flux<ChatStreamPart> executeStreamingChat(ChatRequest request, ChatClient userChatClient) {
        try {
            validateRequest(request);
            String prefixMessage = "";
            AtomicLong elapsedMs = new AtomicLong(NonBlockingAuditAdvisor.NOT_COMPLETED);
            List<Advisor> advisors = new ArrayList<>();
            if (!CollectionUtils.isEmpty(request.getKownledgeId())) {
                // 向量查询条件
                SearchRequest searchRequest = SearchRequest.builder()
                        .topK(RAG_TOP_K)
                        .query(request.getUserPrompt())
                        .similarityThreshold(0.3F)
                        .filterExpression(buildBaseAccessFilter(request.getKownledgeId()))
                        .build();


                QuestionAnswerAdvisor build = QuestionAnswerAdvisor.builder(vectorStore)
                        .searchRequest(searchRequest)
                        .build();
                prefixMessage += "RAG: " + searchRequest.getQuery() + "\n";
                advisors.add(build);
            }

            advisors.add(new NonBlockingAuditAdvisor());
            Object[] enabledToolObject = getEnabledToolObjects(request);

            Map<String, Object> advisorParams = Map.of(
                    "prefixMessage", prefixMessage,
                    NonBlockingAuditAdvisor.ELAPSED_MS_CONTEXT_KEY, elapsedMs);

            // 构建 AI 流式响应
            Flux<ChatStreamPart> source = buildChatPrompt(userChatClient, request)
                    .system(request.getSystemPrompt())
                    .toolCallbacks(getToolCallbacks())
                    // .toolNames(Optional.of(enabledToolNames).or(new String[]{}))
                    .tools(enabledToolObject)
                    .options(request.getChatOptions())
                    .advisors(a ->
                            a.param(ChatMemory.CONVERSATION_ID, request.getConversationId())
                    )
                    .advisors(advisorSpec -> {
                        advisorSpec.params(advisorParams);
                        advisorSpec.advisors(advisors);
                    })
                    .stream()
                    .chatResponse()
                    .map(this::toStreamPart)
                    .transform(parts -> bufferChatParts(parts, STREAM_BATCH_MAX_CHARS, STREAM_BATCH_FLUSH_INTERVAL))
                    .concatWith(Flux.defer(() -> elapsedMs.get() == NonBlockingAuditAdvisor.NOT_COMPLETED
                            ? Flux.empty()
                            : Flux.just(ChatStreamPart.elapsed(elapsedMs.get()))))
                    .timeout(Duration.ofSeconds(300));

            // 累积流式内容并在流终止时执行收尾（持久化 + 释放锁）；
            // 收尾完成后下游才看到终止信号，保证消息已落库、锁已释放
            Flux<ChatStreamPart> aiResponseFlux = accumulateChatParts(source, (signalType, content) -> {
                        if (signalType == SignalType.CANCEL) {
                            handleCancel(request.getChatId());
                        }
                        return completeTermination(request, signalType, content);
                    })
                    .onErrorResume(e -> handleError(request.getChatId(), e));

            // 在 AI 响应前添加前置消息（可选）
            // Flux<String> stringFlux = prefixMessage != null ? aiResponseFlux.startWith(prefixMessage) : aiResponseFlux;
            return aiResponseFlux;

        } catch (Exception e) {
            log.error("executeStreamingChat error:{}", e.getMessage(), e);
            return releaseThenError(request == null ? null : request.getLock(), e);
        }
    }


    public <T> T executeChatWithEntity(ChatRequest request, ChatClient userChatClient, Class<T> entity) {
        try {
            validateRequest(request);
            List<Advisor> advisors = new ArrayList<>();
            // 构建 AI 实体响应，增加超时配置（280秒，略小于 Netty 的 300 秒）
            T entity1 = buildChatPrompt(userChatClient, request)
                    .system(request.getSystemPrompt())
                    .options(ChatOptions.builder()
                            .model(getInnerModel())
                            .maxTokens(8000)
                            .build())
                    .advisors(advisors)
                    .call()
                    .entity(entity);


            return entity1;

        } catch (io.netty.handler.timeout.ReadTimeoutException e) {
            log.error("AI 请求超时，请检查网络连接或稍后重试: {}", e.getMessage(), e);
            throw new RuntimeException("AI 服务响应超时，请稍后重试。如果问题持续存在，请联系管理员", e);
        } catch (Exception e) {
            log.error("executeChatWithEntity error:{}", e.getMessage(), e);
            throw new RuntimeException("AI 服务调用失败: " + e.getMessage(), e);
        }
    }


    private String getInnerModel() {
        return modelConfigService.getInnerModel();
    }

    // meta ==> { "user_id", "knowledge_base_id", "document_id"}
    private String buildBaseAccessFilter(List<String> knowledgeBaseIds) {

        // 如果没有 ID，返回一个 false 的表达式
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
            return "knowledge_base_id in [\"___empty___\"]"; // 不让查询任何知识库
        }
        StringBuilder sb = new StringBuilder();
        sb.append("knowledge_base_id in [");
        for (int i = 0; i < knowledgeBaseIds.size(); i++) {
            if (i != 0) {
                sb.append(",");
            }
            sb.append("\"").append(knowledgeBaseIds.get(i)).append("\"");
        }
        sb.append("]");
        log.info("Vector Search Filter SQL: {}", sb);
        log.info("Vector Search Filter Parameter: {}", knowledgeBaseIds);
        return sb.toString();
    }

    /**
     * 1. 验证请求参数
     */
    private void validateRequest(ChatRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("ChatRequest cannot be null");
        }
        if (request.getSystemPrompt() == null || request.getSystemPrompt().isBlank()) {
            throw new IllegalArgumentException("system prompt cannot be null");
        }
        if (request.getUserPrompt() == null || request.getUserPrompt().isBlank()) {
            throw new IllegalArgumentException("user prompt cannot be null");
        }
        if (request.getRole() == null || request.getRole().isBlank()) {
            throw new IllegalArgumentException("role cannot be null");
        }
    }

    /**
     * 2. 构建聊天 Prompt（根据是否有媒体文件选择不同策略）
     */
    private ChatClient.ChatClientRequestSpec buildChatPrompt(ChatClient client, ChatRequest request) {
        if (request.getMedias() == null || request.getMedias().isEmpty()) {
            return client.prompt().user(p -> p.text(request.getUserPrompt()));
        } else {
            return client.prompt().user(p ->
                    p.text(request.getUserPrompt())
                            .media(request.getMedias().toArray(Media[]::new)));
        }
    }


    private Object[] getEnabledToolObjects(ChatRequest request) {
        if (request == null) {
            return new Object[]{};
        }

        List<String> toolGroupKey = request.getToolGroupKey();
        if (CollectionUtils.isEmpty(toolGroupKey)) {
            return new Object[]{};
        }
        ArrayList<Object> objects = new ArrayList<>();
        for (String toolGroup : toolGroupKey) {
            Object objectTool = MyToolAnnotationAspect.toolBeanMap.get(toolGroup);
            if (!Objects.isNull(objectTool)) {
                objects.add(objectTool);
            }
        }
        return objects.toArray();
    }

    //
    // private String[] getEnabledToolNames(ChatRequest request) {
    //     if (request == null) {
    //         return new String[]{};
    //     }
    //
    //     if (request.getToolGroupKey() == null) {
    //         return new String[]{};
    //     }
    //     Map<String, String> toolGroup = MyToolAnnotationAspect.toolInfoMap.get(request.getToolGroupKey());
    //     if (CollectionUtils.isEmpty(toolGroup)) {
    //         return new String[]{};
    //     }
    //
    //     Set<String> availableToolNames = toolGroup.keySet();
    //     if (CollectionUtils.isEmpty(request.getToolNames())) {
    //         return new String[]{};
    //     }
    //
    //     LinkedHashSet<String> filteredToolNames = new LinkedHashSet<>();
    //     for (String toolName : request.getToolNames()) {
    //         if (availableToolNames.contains(toolName)) {
    //             filteredToolNames.add(toolName);
    //         }
    //     }
    //
    //     return filteredToolNames.toArray(String[]::new);
    // }

    /**
     * 3. 获取 MCP Tool 回调（可缓存优化）
     */
    private ToolCallback[] getToolCallbacks() {
        return mcpToolCallbackProvider.getToolCallbacks();
    }


    /**
     * 5. 处理用户取消
     */
    private void handleCancel(String chatId) {
        log.info("用户主动取消对话 chatId:{}", chatId);
    }

    /**
     * 6. 处理完成回调（释放锁 + 发布事件）
     *
     * <p>流终止时的收尾逻辑：持久化 AI 回复 + 释放分布式锁</p>
     * <p>ON_COMPLETE 路径：先持久化，持久化失败也释放锁再抛出持久化异常，
     *    保证锁在任何情况下都被释放</p>
     * <p>ON_ERROR/CANCEL 路径：持久化和释放均为 best-effort，失败只记日志</p>
     */
    private Mono<Void> completeTermination(ChatRequest request, SignalType signalType, String content) {
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
        return persist.onErrorResume(error -> {
                    log.error("聊天终止持久化失败, signalType={}", signalType, error);
                    return Mono.empty();
                })
                .then(release.onErrorResume(error -> {
                    log.error("聊天锁异步释放失败, signalType={}", signalType, error);
                    return Mono.empty();
                }));
    }

    /**
     * 7. 安全释放分布式锁
     */
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

    /**
     * 8. 错误处理
     */
    private Flux<ChatStreamPart> handleError(String chatId, Throwable e) {
        log.error("Chat Error [{}]: {}", chatId, e.getMessage());
        return propagateStreamingError(e);
    }

    static Flux<ChatStreamPart> propagateStreamingError(Throwable error) {
        return Flux.error(error);
    }

    /**
     * 管道在流式响应启动前就失败时使用：先释放锁，再抛出原始异常。
     * 释放失败不影响原始异常的传播（仅记日志）。
     * 使用 Flux.defer 确保懒订阅——只有被订阅时才执行释放。
     */
    public static Flux<ChatStreamPart> releaseThenError(ChatLockHandle lock, Throwable original) {
        if (lock == null) {
            return Flux.error(original);
        }
        return Flux.defer(() -> {
            Sinks.One<Void> releaseResult = Sinks.one();
            lock.release().subscribe(
                    ignored -> { },
                    error -> {
                        log.error("聊天锁释放失败，保留原始异常", error);
                        releaseResult.tryEmitEmpty();
                    },
                    releaseResult::tryEmitEmpty);
            return releaseResult.asMono().thenMany(Flux.error(original));
        });
    }

    private ChatStreamPart toStreamPart(ChatResponse response) {
        if (response == null) {
            return new ChatStreamPart(null, null, null);
        }
        Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
        Long promptTokens = usage == null ? null : toLong(usage.getPromptTokens());
        Long completionTokens = usage == null ? null : toLong(usage.getCompletionTokens());
        Long totalTokens = usage == null ? null : toLong(usage.getTotalTokens());
        ChatStreamPart.TokenUsage tokenUsage = promptTokens == null || completionTokens == null || totalTokens == null
                ? null : new ChatStreamPart.TokenUsage(promptTokens, completionTokens, totalTokens);

        if (response.getResult() == null || response.getResult().getOutput() == null) {
            // 流式 API 的最后一个响应可能只有 usage，没有正文 output。
            return ChatStreamPart.usage(tokenUsage);
        }

        AssistantMessage output = response.getResult().getOutput();
        Object reasoning = output.getMetadata() == null ? null : output.getMetadata().get("reasoningContent");
        return new ChatStreamPart(output.getText(), reasoning == null ? null : reasoning.toString(), tokenUsage);
    }

    private Long toLong(Number value) {
        return value == null ? null : value.longValue();
    }

    /** 合并过碎的同类增量，类型切换和 usage 元数据会立即刷出。 */
    static Flux<ChatStreamPart> bufferChatParts(Flux<ChatStreamPart> source,
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

    /**
     * accumulatePerSubscription 的简化版（不累积内容），仅用于单元测试。
     * 生产代码请使用 accumulatePerSubscription。
     */
    static Flux<String> completeBeforeTermination(Flux<String> source,
                                                   Consumer<SignalType> onTerminate) {
        return Flux.defer(() -> {
            AtomicBoolean terminated = new AtomicBoolean();
            Consumer<SignalType> terminateOnce = signalType -> {
                if (terminated.compareAndSet(false, true)) {
                    onTerminate.accept(signalType);
                }
            };
            return source
                    .doOnComplete(() -> terminateOnce.accept(SignalType.ON_COMPLETE))
                    .doOnError(error -> terminateOnce.accept(SignalType.ON_ERROR))
                    .doOnCancel(() -> terminateOnce.accept(SignalType.CANCEL));
        });
    }

    /**
     * 累积流式内容并在流终止时执行一次性收尾回调。
     *
     * <p>核心设计：</p>
     * <ol>
     *   <li>Flux.defer — 每次订阅创建独立的状态（累积器、终止标志、结果 Sink）</li>
     *   <li>materialize/concatMap/dematerialize — 拦截 ON_COMPLETE/ON_ERROR 信号，
     *       在放行前等待收尾完成（下游看不到终止信号直到收尾结束）</li>
     *   <li>Sinks.One + fire-and-forget subscribe — 收尾作为独立订阅运行，
     *       不受下游取消影响（下游取消只取消对结果的等待，不取消收尾本身）</li>
     *   <li>AtomicBoolean terminated — 保证收尾只执行一次（ON_COMPLETE/ON_ERROR/CANCEL
     *       三者只有一个能触发收尾）</li>
     * </ol>
     *
     * @param source      原始流式响应
     * @param onTerminate 收尾回调，接收（信号类型, 累积内容），返回收尾 Mono
     */
    static Flux<String> accumulatePerSubscription(Flux<String> source,
                                                   BiFunction<SignalType, String, Mono<Void>> onTerminate) {
        return Flux.defer(() -> {
            StringBuilder contentAccumulator = new StringBuilder(); // 累积所有 chunk 供持久化
            AtomicBoolean terminated = new AtomicBoolean();          // 保证收尾只执行一次
            Sinks.One<Void> cleanupResult = Sinks.one();            // 收尾结果协调：startCleanup 启动收尾，concatMap 等待结果
            BiFunction<SignalType, String, Mono<Void>> startCleanup = (signalType, content) -> {
                if (terminated.compareAndSet(false, true)) {
                    // fire-and-forget 订阅：收尾独立于反应链运行，下游取消不影响正在进行的收尾
                    onTerminate.apply(signalType, content)
                            .subscribe(
                                    ignored -> { },
                                    error -> cleanupResult.tryEmitError(error),
                                    () -> cleanupResult.tryEmitEmpty());
                }
                return cleanupResult.asMono(); // 已终止时返回已有结果，未终止时等待收尾完成
            };
            return source
                    .doOnNext(contentAccumulator::append)
                    .materialize()           // 转为 Signal 流，拦截终止信号
                    .concatMap(signal -> {
                        SignalType signalType = signal.getType();
                        if (signalType == SignalType.ON_COMPLETE || signalType == SignalType.ON_ERROR) {
                            Mono<Void> termination = startCleanup.apply(signalType, contentAccumulator.toString());
                            if (signalType == SignalType.ON_ERROR) {
                                // ON_ERROR 时收尾失败不掩盖原始错误，仅记日志
                                termination = termination.onErrorResume(error -> {
                                    log.error("聊天异常收尾失败，保留原始异常", error);
                                    return Mono.empty();
                                });
                            }
                            return termination.thenReturn(signal); // 收尾完成后放行终止信号
                        }
                        return Mono.just(signal); // ON_NEXT 直接透传
                    })
                    .<String>dematerialize()   // 还原为 String 流
                    .doOnCancel(() -> {
                        // CANCEL 时 fire-and-forget 触发收尾（下游已取消，无法传播结果）
                        startCleanup.apply(SignalType.CANCEL, contentAccumulator.toString())
                                .subscribe(
                                        ignored -> { },
                                        error -> log.error("聊天取消异步清理失败", error));
                    });
        });
    }

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

    /**
     * 9. 构建友好的错误消息
     */
    private String buildFriendlyErrorMessage(String message) {
        return "⚠️错误信息: " + message;
    }

}
