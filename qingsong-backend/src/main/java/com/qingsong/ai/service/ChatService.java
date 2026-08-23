package com.qingsong.ai.service;

import com.qingsong.ai.advice.NonBlockingAuditAdvisor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.content.Media;
import org.springframework.ai.mcp.AsyncMcpToolCallbackProvider;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
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
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * AI 聊天服务类 —— 负责流式聊天的用例编排（协议无关）。
 *
 * <p>拆分说明：</p>
 * <ul>
 *   <li>工具装配见 {@link ToolRegistry}；</li>
 *   <li>RAG 知识库 advisor 见 {@link RagContextProvider}；</li>
 *   <li>流终止收尾（持久化 + 解锁）见 {@link ChatTerminationHandler}；</li>
 *   <li>非流式实体调用见 {@link ChatEntityService}；</li>
 *   <li>正文分批见 {@link ChatStreamBuffer}。</li>
 * </ul>
 *
 * <p>本类保留：{@link #executeStreamingChat} 主流程、MCP 工具回调装配、
 * {@code ChatResponse} → {@link ChatStreamPart} 映射、错误传播与锁释放兜底。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private static final Duration STREAM_TIMEOUT = Duration.ofSeconds(300);

    private final AsyncMcpToolCallbackProvider mcpToolCallbackProvider;
    private final ToolRegistry toolRegistry;
    private final RagContextProvider ragContextProvider;
    private final ChatTerminationHandler chatTerminationHandler;
    private final ChatEntityService chatEntityService;

    /**
     * 执行流式 AI 对话。
     *
     * @param request        聊天请求上下文
     * @param userChatClient ChatClient 实例
     * @return Flux<ChatStreamPart> 流式响应
     */
    public Flux<ChatStreamPart> executeStreamingChat(ChatRequest request, ChatClient userChatClient) {
        try {
            validateRequest(request);
            String prefixMessage = "";
            AtomicLong elapsedMs = new AtomicLong(NonBlockingAuditAdvisor.NOT_COMPLETED);
            List<Advisor> advisors = new ArrayList<>();
            if (!CollectionUtils.isEmpty(request.getKownledgeId())) {
                // 知识库 RAG：构造向量检索 advisor 注入上下文
                QuestionAnswerAdvisor build = ragContextProvider.buildQuestionAnswerAdvisor(
                        request.getKownledgeId(), request.getUserPrompt());
                prefixMessage += "RAG: " + request.getUserPrompt() + "\n";
                advisors.add(build);
            }

            advisors.add(new NonBlockingAuditAdvisor());
            Object[] enabledToolObject = getEnabledToolObjects(request);

            // 每请求一个工具事件总线：包装器写入、流内排空，避免并发会话事件串流
            ToolExecutionEventBus toolEventBus = new ToolExecutionEventBus();

            Map<String, Object> advisorParams = Map.of(
                    "prefixMessage", prefixMessage,
                    NonBlockingAuditAdvisor.ELAPSED_MS_CONTEXT_KEY, elapsedMs);

            // 双通道工具装配：MCP 回调 + @MyTools 分组工具，统一包装为 ObservingToolCallback。
            // ⚠️ 必须走 .toolCallbacks()：.tools(Object...) 只接受 @Tool 注解的 Bean，
            // 不接受 ToolCallback 实例（否则报 "No @Tool annotated methods found"）。
            ToolCallback[] wrappedCallbacks = mergeToolCallbacks(
                    wrapAll(mcpToolCallbackProvider.getToolCallbacks(), toolEventBus),
                    wrapAll(ToolCallbacks.from(enabledToolObject), toolEventBus));

            // 构建 AI 流式响应
            Flux<ChatStreamPart> source = buildChatPrompt(userChatClient, request)
                    .system(request.getSystemPrompt())
                    .toolCallbacks(wrappedCallbacks)
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
                    // 每个 part 前先排空工具事件总线，保证 tool_call/tool_result 先于后续正文
                    .concatMap(part -> Flux.concat(toolEventBus.drain(), Mono.just(part)))
                    .transform(parts -> ChatStreamBuffer.bufferChatParts(parts,
                            ChatStreamBuffer.STREAM_BATCH_MAX_CHARS, ChatStreamBuffer.STREAM_BATCH_FLUSH_INTERVAL))
                    .concatWith(Flux.defer(() -> elapsedMs.get() == NonBlockingAuditAdvisor.NOT_COMPLETED
                            ? Flux.empty()
                            : Flux.just(ChatStreamPart.elapsed(elapsedMs.get()))))
                    // 流尾补排一次，避免末尾工具事件被遗漏
                    .concatWith(Flux.defer(toolEventBus::drain))
                    // 整体超时 300s：与 Netty 300s 响应超时保持一致（见 ChatClientFactory/底层 RetryTemplate）
                    .timeout(STREAM_TIMEOUT);

            // 累积流式内容并在流终止时执行收尾（持久化 + 释放锁）；
            // 收尾完成后下游才看到终止信号，保证消息已落库、锁已释放
            Flux<ChatStreamPart> aiResponseFlux = ChatTerminationHandler.accumulateChatParts(source, (signalType, content) -> {
                        if (signalType == SignalType.CANCEL) {
                            chatTerminationHandler.handleCancel(request.getChatId());
                        }
                        return chatTerminationHandler.completeTermination(request, signalType, content);
                    })
                    .onErrorResume(e -> handleError(request.getChatId(), e));

            return aiResponseFlux;

        } catch (Exception e) {
            log.error("executeStreamingChat error:{}", e.getMessage(), e);
            return releaseThenError(request == null ? null : request.getLock(), e);
        }
    }

    /**
     * 非流式实体对话（兼容保留：CodeSnippet 域调用入口，实现见 {@link ChatEntityService}）。
     */
    public <T> T executeChatWithEntity(ChatRequest request, ChatClient userChatClient, Class<T> entity) {
        return chatEntityService.executeChatWithEntity(request, userChatClient, entity);
    }

    /**
     * 请求参数校验（包内共享：{@link ChatEntityService} 复用）。
     */
    static void validateRequest(ChatRequest request) {
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
     * 构建聊天 Prompt（根据是否有媒体文件选择不同策略；包内共享）。
     */
    static ChatClient.ChatClientRequestSpec buildChatPrompt(ChatClient client, ChatRequest request) {
        if (request.getMedias() == null || request.getMedias().isEmpty()) {
            return client.prompt().user(p -> p.text(request.getUserPrompt()));
        } else {
            return client.prompt().user(p ->
                    p.text(request.getUserPrompt())
                            .media(request.getMedias().toArray(Media[]::new)));
        }
    }

    /**
     * 按请求的 {@code toolGroupKey} 从 {@link ToolRegistry} 取出启用的工具 Bean。
     */
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
            Object objectTool = toolRegistry.getToolGroup(toolGroup);
            if (!Objects.isNull(objectTool)) {
                objects.add(objectTool);
            }
        }
        return objects.toArray();
    }

    /**
     * 把工具回调统一包装为 {@link ObservingToolCallback}，接入事件观测。
     */
    private ToolCallback[] wrapAll(ToolCallback[] callbacks, ToolExecutionEventBus eventBus) {
        if (callbacks == null || callbacks.length == 0) {
            return new ToolCallback[0];
        }
        ToolCallback[] wrapped = new ToolCallback[callbacks.length];
        for (int i = 0; i < callbacks.length; i++) {
            wrapped[i] = new ObservingToolCallback(callbacks[i], eventBus);
        }
        return wrapped;
    }

    /**
     * 合并两路工具回调（MCP + @MyTools）。
     */
    private static ToolCallback[] mergeToolCallbacks(ToolCallback[] first, ToolCallback[] second) {
        ToolCallback[] merged = new ToolCallback[first.length + second.length];
        System.arraycopy(first, 0, merged, 0, first.length);
        System.arraycopy(second, 0, merged, first.length, second.length);
        return merged;
    }

    /**
     * 流内异常处理。
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

    /**
     * accumulatePerSubscription 的简化版（不累积内容），仅用于单元测试。
     * 生产代码请使用 {@link ChatTerminationHandler#accumulateChatParts}。
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
     * String 版本的流式累积（仅测试使用）。生产使用
     * {@link ChatTerminationHandler#accumulateChatParts}（ChatStreamPart 版本），
     * 两者语义重复，勿在生产调用本方法。
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

}
