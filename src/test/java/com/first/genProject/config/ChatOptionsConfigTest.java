package com.first.genProject.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import com.first.genProject.dto.InterviewSelection;
import com.first.genProject.service.AIServiceImpl;
import com.first.genProject.service.InterviewService;
import com.first.genProject.support.StubChatModel;

class ChatOptionsConfigTest {
    @Test
    void bothServicesUseSharedModelAndOptions() {
        var model = new StubChatModel();
        var client = new ChatOptionsConfig().chatClient(ChatClient.builder(model), "configured-model");

        new AIServiceImpl(client).chat("Explain Java");
        assertEquals("configured-model", model.prompt.getOptions().getModel());
        assertEquals(1000, model.prompt.getOptions().getMaxTokens());

        new InterviewService(client).generateQuestion(new InterviewSelection("Java", "Spring Boot", "intermediate"));
        assertEquals("configured-model", model.prompt.getOptions().getModel());
        assertEquals(1000, model.prompt.getOptions().getMaxTokens());
    }
}
