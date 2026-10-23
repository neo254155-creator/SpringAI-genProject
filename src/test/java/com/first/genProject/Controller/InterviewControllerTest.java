package com.first.genProject.Controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.first.genProject.service.InterviewService;
import com.first.genProject.dto.InterviewSelection;

class InterviewControllerTest {
    private final StubModel model = new StubModel();
    private final InterviewService service = new InterviewService(ChatClient.create(model));
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new InterviewController(service)).setControllerAdvice(new ApiExceptionHandler()).build();
    private static final String SELECTION = """
            {"track":"Java","topic":"Spring Boot","level":"intermediate"}
            """;

    @Test
    void listsSupportedSelectionsWithoutCallingModel() throws Exception {
        mvc.perform(get("/interview/options")).andExpect(status().isOk())
                .andExpect(jsonPath("$.tracks[0]").value("Java"))
                .andExpect(jsonPath("$.topics[1]").value("Spring Boot"))
                .andExpect(jsonPath("$.levels[1]").value("intermediate"));
        assertEquals(0, model.calls);
    }

    @Test
    void returnsQuestionAndRendersSelectionIntoPrompt() throws Exception {
        mvc.perform(post("/interview/questions").contentType(MediaType.APPLICATION_JSON).content(SELECTION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question").value("How does auto-configuration work?"))
                .andExpect(jsonPath("$.topic").value("Spring Boot"))
                .andExpect(jsonPath("$.level").value("intermediate"));
        assertEquals(1, model.calls);
        String user = model.prompt.getUserMessage().getText();
        assertTrue(user.contains("Java"));
        assertTrue(user.contains("Spring Boot"));
        assertTrue(user.contains("intermediate"));
        assertFalse(user.contains("{topic}"));
        assertTrue(model.prompt.getSystemMessage().getText().contains("exactly one"));
        assertEquals(500, model.prompt.getOptions().getMaxTokens());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "{", "{}", """
            {"track":"Python","topic":"Spring Boot","level":"intermediate"}
            """, """
            {"track":"Java","topic":"unknown","level":"intermediate"}
            """, """
            {"track":"Java","topic":"Spring Boot","level":"expert"}
            """})
    void rejectsInvalidSelectionsBeforeProviderCall(String body) throws Exception {
        mvc.perform(post("/interview/questions").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        assertEquals(0, model.calls);
    }

    @Test
    void rejectsEmptyProviderResponse() throws Exception {
        model.response = " ";
        mvc.perform(post("/interview/questions").contentType(MediaType.APPLICATION_JSON).content(SELECTION))
                .andExpect(status().isBadGateway());
    }

    @Test
    void handlesProviderFailureWithoutLeakingDetails() throws Exception {
        model.fail = true;
        mvc.perform(post("/interview/questions").contentType(MediaType.APPLICATION_JSON).content(SELECTION))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("AI_PROVIDER_ERROR"));
    }

    @Test
    void serviceAlsoValidatesDirectCalls() {
        assertThrows(IllegalArgumentException.class, () -> service.generateQuestion(null));
        assertThrows(IllegalArgumentException.class,
                () -> service.generateQuestion(new InterviewSelection("Java", " ", "beginner")));
        assertEquals(0, model.calls);
    }

    static class StubModel implements ChatModel {
        int calls;
        Prompt prompt;
        String response = "How does auto-configuration work?";
        boolean fail;
        @Override
        public ChatOptions getOptions() { return ChatOptions.builder().build(); }
        @Override
        public ChatResponse call(Prompt prompt) {
            this.prompt = prompt;
            calls++;
            if (fail) throw new IllegalStateException("private provider details");
            return new ChatResponse(List.of(new Generation(new AssistantMessage(response))));
        }
    }
}
