package com.first.genProject.Controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.first.genProject.service.AIServiceImpl;
import com.first.genProject.support.StubChatModel;

class AIControllerTest {
    private final StubChatModel model = new StubChatModel();
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
            new AIController(new AIServiceImpl(ChatClient.create(model))))
            .setControllerAdvice(new ApiExceptionHandler()).build();

    @Test
    void getAcceptsQueryAndReturnsText() throws Exception {
        mvc.perform(get("/chat").param("q", "Explain dependency injection"))
                .andExpect(status().isOk()).andExpect(content().string("Stub response"));
        assertEquals("Explain dependency injection", model.prompt.getUserMessage().getText().trim());
    }

    @Test
    void postAcceptsMessageAndReturnsJson() throws Exception {
        mvc.perform(post("/chat").contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"Explain Spring beans\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").value("Stub response"));
        assertEquals("Explain Spring beans", model.prompt.getUserMessage().getText().trim());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "{", "{}", "{\"message\":null}", "{\"message\":\" \"}"})
    void rejectsInvalidBodiesWithoutCallingModel(String body) throws Exception {
        mvc.perform(post("/chat").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        assertEquals(0, model.calls);
    }

    @Test
    void rejectsMissingBlankAndOversizedQuery() throws Exception {
        mvc.perform(get("/chat")).andExpect(status().isBadRequest());
        for (String value : new String[] {" ", "x".repeat(8001)}) {
            mvc.perform(get("/chat").param("q", value)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
        assertEquals(0, model.calls);
    }

    @Test
    void preservesHttpMethodAndMediaTypeErrors() throws Exception {
        mvc.perform(put("/chat")).andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
        mvc.perform(post("/chat").contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.status").value(415));
        assertEquals(0, model.calls);
    }

    @Test
    void hidesProviderExceptionDetails() throws Exception {
        model.failure = new IllegalArgumentException("private provider token");
        mvc.perform(get("/chat").param("q", "Hello"))
                .andExpect(status().isBadGateway())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AI_PROVIDER_ERROR"))
                .andExpect(jsonPath("$.detail").value("The AI provider could not complete the request. Please try again."));
    }

    @Test
    void applicationErrorsAre500InsteadOfProviderErrors() throws Exception {
        var broken = MockMvcBuilders.standaloneSetup(new AIController(message -> {
            throw new IllegalStateException("internal details");
        })).setControllerAdvice(new ApiExceptionHandler()).build();
        broken.perform(get("/chat").param("q", "Hello"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }
}
