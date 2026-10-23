package com.first.genProject.service;

import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.first.genProject.exception.AiProviderException;
import com.first.genProject.exception.InvalidInputException;

@Service
public class AIServiceImpl implements AiService {
    private final ChatClient chatClient;
    private final PromptTemplate userTemplate;
    private final SystemPromptTemplate systemTemplate;

    public AIServiceImpl(ChatClient chatClient) {
        this.chatClient = chatClient;
        this.userTemplate = new PromptTemplate(new ClassPathResource("Prompt/UserMessage"));
        this.systemTemplate = new SystemPromptTemplate(new ClassPathResource("Prompt/SystemMessage"));
    }

    @Override
    public String chat(String message) {
        if (message == null || message.isBlank()) {
            throw new InvalidInputException("message must not be blank");
        }
        if (message.length() > 8000) {
            throw new InvalidInputException("message must be at most 8000 characters");
        }
        Prompt prompt = new Prompt(systemTemplate.createMessage(),
                userTemplate.createMessage(Map.of("message", message.trim())));
        final String response;
        try {
            response = chatClient.prompt(prompt)
                    .options(OpenAiChatOptions.builder().temperature(0.2).maxTokens(1000))
                    .call().content();
        } catch (RuntimeException exception) {
            throw new AiProviderException(exception);
        }
        if (response == null || response.isBlank()) {
            throw new AiProviderException();
        }
        return response;
    }
}
