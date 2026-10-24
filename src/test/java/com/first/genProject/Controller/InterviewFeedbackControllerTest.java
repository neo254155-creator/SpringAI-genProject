package com.first.genProject.Controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.first.genProject.support.FeedbackFixtures.*;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.first.genProject.service.InterviewFeedbackService;
import com.first.genProject.support.StubChatModel;

class InterviewFeedbackControllerTest {
    private final StubChatModel model = new StubChatModel();
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new InterviewFeedbackController(
            new InterviewFeedbackService(ChatClient.create(model))))
            .setControllerAdvice(new ApiExceptionHandler()).build();

    @Test
    void returnsStructuredFeedback() throws Exception {
        model.response = VALID_FEEDBACK;
        mvc.perform(post("/interview/feedback").contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(7))
                .andExpect(jsonPath("$.maxScore").value(10))
                .andExpect(jsonPath("$.rubric.correctness.score").value(4))
                .andExpect(jsonPath("$.explanation").isString())
                .andExpect(jsonPath("$.correctPoints").isArray())
                .andExpect(jsonPath("$.missingPoints").isArray());
    }

    static Stream<String> invalidRequests() {
        return Stream.of("", "null", "{", "{}", VALID_REQUEST.replace("Java", "Python"),
                VALID_REQUEST.replace("intermediate", "expert"),
                VALID_REQUEST.replace("How does dependency injection work?", " "),
                VALID_REQUEST.replace("Spring supplies dependencies to objects.", " "),
                VALID_REQUEST.replace("Spring supplies dependencies to objects.", "a".repeat(8001)));
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void rejectsBadInputWithoutCallingProvider(String body) throws Exception {
        mvc.perform(post("/interview/feedback").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        assertEquals(0, model.calls);
    }

    @Test
    void invalidModelJsonReturnsSafe502() throws Exception {
        model.response = "private invalid provider content";
        mvc.perform(post("/interview/feedback").contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("AI_PROVIDER_ERROR"))
                .andExpect(jsonPath("$.detail").value(
                        "The AI provider could not complete the request. Please try again."));
    }
}
