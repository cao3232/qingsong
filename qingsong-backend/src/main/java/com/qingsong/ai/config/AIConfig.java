package com.qingsong.ai.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.Setter;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * description...
 *
 * @author : caojiangjiang
 * @data : 2026/02/08 22:02
 */
@Configuration
@ConfigurationProperties(prefix = "spring.ai.openai")
@Setter
public class AIConfig {

    private String apiKey;
    private String baseUrl;

    // @Autowired
    // @Lazy
    // private ModelSourceService modelSourceService;


    @Bean
    OpenAiApi openAiApi() {
        // 创建连接池配置，增强连接稳定性
        ConnectionProvider connectionProvider = ConnectionProvider.builder("ai-api-pool")
                .maxConnections(50)
                .pendingAcquireTimeout(Duration.ofSeconds(60))
                .evictInBackground(Duration.ofSeconds(300))
                .lifo() // 后进先出，提高连接复用率
                .build();

        // 配置 HttpClient，增加多种超时和稳定性机制
        // 🔑 Netty 默认会使用系统 DNS 配置，无需额外设置
        HttpClient httpClient = HttpClient.create(connectionProvider)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(300, TimeUnit.SECONDS))  // 读取超时 300 秒
                        .addHandlerLast(new WriteTimeoutHandler(300, TimeUnit.SECONDS)))  // 写入超时 300 秒
                // 配置 TCP 相关参数
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.SO_REUSEADDR, true);

        WebClient.Builder webClientBuilder = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024));

        // ModelSource modelSource = modelSourceService.getCurrentModelSource();
        // if (modelSource == null) {
        //     modelSource = new ModelSource();
        // }

        return new OpenAiApi.Builder()
                // .apiKey(Optional.ofNullable(modelSource.getApiKey()).orElse(apiKey))
                // .baseUrl(Optional.ofNullable(modelSource.getApiBaseUrl()).orElse(baseUrl))
                // 设置为一个假的
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .webClientBuilder(webClientBuilder)
                .build();
    }

}
