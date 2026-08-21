package com.qingsong.ai.service.factorys;

import com.qingsong.ai.entity.exception.BusinessException;
import com.qingsong.ai.entity.po.model.ModelSource;
import com.qingsong.ai.mapper.model.ModelSourceMapper;
import io.netty.channel.ChannelOption;
import jakarta.annotation.PostConstruct;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * description...
 *
 * @author : caojiangjiang
 * @data : 2026/03/22 22:27
 */
@Component
public class ChatClientFactory {
    private final ConcurrentHashMap<String, ChatClient> chatClientMap = new ConcurrentHashMap<>();

    private final ModelSourceMapper modelSourceMapper;
    private final OpenAiApi openAiApi;
    private final ChatMemory chatMemory;

    private volatile ChatClient defaultChatClient;

    public ChatClientFactory(ModelSourceMapper modelSourceMapper, OpenAiApi openAiApi, ChatMemory chatMemory) {
        this.modelSourceMapper = modelSourceMapper;
        this.openAiApi = openAiApi;
        this.chatMemory = chatMemory;
    }

    public void setDefault(String sourceCode) {
        ChatClient chatClient = chatClientMap.get(sourceCode);
        if (chatClient == null) {
            throw new BusinessException("模型来源不存在，无法切换");
        }
        defaultChatClient = chatClient;
    }

    public ChatClient getDefaultChatClient() {
        return defaultChatClient;
    }

    public void addChatClient(ModelSource modelSource) {
        ChatClient exist = chatClientMap.get(modelSource.getSourceCode());
        if (exist != null) {
            throw new BusinessException("模型来源已经存在配置：" + modelSource.getSourceName());
        }

        createChatClient(modelSource);
    }

    @PostConstruct
    public void init() {
        List<ModelSource> modelSources = modelSourceMapper.selectList(null);
        modelSources.forEach(modelSource -> {
            ChatClient chatClient = createChatClient(modelSource);

            if (modelSource.getIsActive()) {
                this.defaultChatClient = chatClient;
            }
        });

    }

    private @NotNull ChatClient createChatClient(ModelSource modelSource) {
        ChatClient chatClient = genarateChatClient(modelSource);
        // 添加到容器里
        chatClientMap.put(modelSource.getSourceCode(), chatClient);
        return chatClient;
    }

    private @NotNull ChatClient genarateChatClient(ModelSource modelSource) {
        // // 创建连接池配置，增强连接稳定性
        // ConnectionProvider connectionProvider = ConnectionProvider.builder("ai-api-pool-" + modelSource.getSourceCode())
        //         .maxConnections(50)
        //         .pendingAcquireTimeout(Duration.ofSeconds(60))
        //         .maxIdleTime(Duration.ofSeconds(300))      // 空闲时间 > 响应超时
        //         .maxLifeTime(Duration.ofSeconds(600))      // 生命周期 > 响应超时（关键！）
        //         .evictInBackground(Duration.ofSeconds(300))
        //         .lifo()
        //         .build();
        //
        // HttpClient httpClient = HttpClient.create(connectionProvider)
        //         .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 60000)
        //         .option(ChannelOption.SO_KEEPALIVE, true)
        //         .responseTimeout(Duration.ofSeconds(300))  // 只用这个控制整体超时
        //         .option(ChannelOption.TCP_NODELAY, true);
        //
        // // 创建带有超时配置的 RestClient（关键修复：覆盖 Spring AI 默认的 10 秒超时）
        // ClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory() {{
        //     setConnectTimeout((int) Duration.ofSeconds(60).toMillis());  // 连接超时 60 秒
        //     setReadTimeout((int) Duration.ofSeconds(300).toMillis());    // 读取超时 300 秒（与 Netty 一致）
        // }};
        //
        // RestClient restClient = RestClient.builder()
        //         .requestFactory(requestFactory)
        //         .build();
        //
        // // 创建带有超时和代理配置的 WebClient
        // WebClient.Builder webClientBuilder = WebClient.builder()
        //         .clientConnector(new ReactorClientHttpConnector(httpClient))
        //         .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024));
        //
        //
        // // 新建openAiApi，并应用自定义的 WebClient 和 RestClient 配置
        // OpenAiApi aiApi = OpenAiApi.builder()
        //         .apiKey(modelSource.getApiKey())
        //         .baseUrl(modelSource.getApiBaseUrl())
        //         .webClientBuilder(webClientBuilder)
        //         .restClientBuilder(RestClient.builder().requestFactory(requestFactory))  // 🔑 关键：注入 RestClient 超时配置
        //         .build();
        //
        // OpenAiChatModel chatModel = OpenAiChatModel.builder()
        //         .openAiApi(aiApi)
        //         // 🔑 关键修复：创建自定义 RetryTemplate，覆盖默认的 10 秒超时
        //         .retryTemplate(createCustomRetryTemplate())
        //         .build();


        OpenAiApi aiApi = OpenAiApi.builder()
                .apiKey(modelSource.getApiKey())
                .baseUrl(modelSource.getApiBaseUrl())
                .build();

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(aiApi)
                // 🔑 关键修复：创建自定义 RetryTemplate，覆盖默认的 10 秒超时
                .retryTemplate(createCustomRetryTemplate())
                .build();

        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();
        return chatClient;

    }

    /**
     * 🔑 关键修复：创建自定义 RetryTemplate，覆盖默认的 10 秒超时
     * Spring AI 默认使用 10 秒超时，这会导致长文本生成时提前超时
     */
    private RetryTemplate createCustomRetryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();

        // 重试策略：最多重试 1 次（不重试）
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(1);  // 不重试，避免重复调用 AI API
        retryTemplate.setRetryPolicy(retryPolicy);

        // 退避策略：固定 2 秒退避（如果启用重试）
        FixedBackOffPolicy backOffPolicy = new FixedBackOffPolicy();
        backOffPolicy.setBackOffPeriod(2000);  // 2 秒
        retryTemplate.setBackOffPolicy(backOffPolicy);

        return retryTemplate;
    }

    public void refreshChatClient(ModelSource modelSource) {
        chatClientMap.replace(modelSource.getSourceCode(), genarateChatClient(modelSource));
        if (modelSource.getIsActive()) {
            this.defaultChatClient = chatClientMap.get(modelSource.getSourceCode());
        }
    }

    public ChatClient getChatClient(String sourceCode) {
        ChatClient chatClient = chatClientMap.get(sourceCode);
        if (chatClient == null) {
            throw new BusinessException("模型来源不存在");
        }
        return chatClient;
    }

    public boolean removeChatClient(String sourceCode) {
        ChatClient chatClient = chatClientMap.get(sourceCode);
        if (chatClient != null) {
            chatClientMap.remove(sourceCode);
        }
        return true;
    }
}
