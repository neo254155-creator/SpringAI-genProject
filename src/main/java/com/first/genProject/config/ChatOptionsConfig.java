package com.first.genProject.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatOptionsConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder,
            @Value("${spring.ai.openai.chat.model}") String model) {
        return builder.defaultOptions(OpenAiChatOptions.builder()
                .model(model)
                .temperature(0.2)
                .maxTokens(1000))
                .build();
    }
}
