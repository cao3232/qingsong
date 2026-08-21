// package com.qingsong.ai.service.impl;
//
// import com.qingsong.ai.service.ChatService;
// import lombok.extern.slf4j.Slf4j;
// import org.springframework.ai.chat.client.ChatClient;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.stereotype.Service;
//
// import java.util.Map;
//
// /**
//  * description...
//  *
//  * @author : caojiangjiang
//  * @data : 2025/11/26 15:53
//  */
// @Service
// @Slf4j
// public class ChatServiceImpl implements ChatService {
//
//     @Autowired
//     private Map<String, ChatClient> chatClients;
//
//
//     @Override
//     public String chat(String prompt) {
//         log.info("prompt: {}", prompt);
//         ChatClient userChatClient = chatClients.get("chatClient");
//         return userChatClient.prompt()
//                 .user(prompt)
//                 .call()
//                 .content();
//     }
//
//
//     @Override
//     public <T> T chatWithEntity(String prompt, T entity) {
//         log.info("prompt: {}", prompt);
//         ChatClient userChatClient = chatClients.get("geminiFlashChatClient");
//         return userChatClient.prompt()
//                 .user(prompt)
//                 .call()
//                 .entity((Class<T>) entity.getClass());
//     }
// }
