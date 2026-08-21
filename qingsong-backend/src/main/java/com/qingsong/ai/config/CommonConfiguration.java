package com.qingsong.ai.config;

import com.qingsong.ai.repository.MyMessageWindowChatMemory;
import com.qingsong.ai.repository.RedisBackedChatMemoryRepository;
import com.qingsong.ai.tools.AITools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestTemplate;

@Configuration
public class CommonConfiguration {

    @Autowired
    @Lazy
    private AITools aiTools;

    @Bean
    public MyMessageWindowChatMemory chatMemory(RedisBackedChatMemoryRepository redisBackedChatMemoryRepository) {
        return MyMessageWindowChatMemory.builder()
                .chatMemoryRepository(redisBackedChatMemoryRepository)
                .build();
    }

    @Bean
    @Primary
    OpenAiChatModel openAiChatModel(OpenAiApi openAiApi) {
        return OpenAiChatModel.builder().openAiApi(openAiApi).build();
    }


    @Bean
    public ChatClient chatClient(OpenAiChatModel model, ChatMemory chatMemory) {
        return ChatClient
                .builder(model)
                // TODO 这里可能会覆盖
                // .defaultOptions(ChatOptions.builder().model("gemini-2.5-flash-preview-04-17").build())
                // .defaultOptions(ToolCallingChatOptions.builder()
                //         .toolCallbacks(ToolCallbacks.from(aiTools))
                //         .build())
                // .defaultSystem("你是一个热心、可爱的智能助手，你的名字叫小江子，请以
                // 小江子的身份和语气回答问题。")
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        // new MessageChatMemoryAdvisor(chatMemory)
                        MessageChatMemoryAdvisor.builder(chatMemory).build()// chat-memory advisor
                )
                // .defaultTools(aiTools)
                .build();
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
