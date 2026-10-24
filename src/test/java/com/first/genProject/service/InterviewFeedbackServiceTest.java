package com.first.genProject.service;

import static org.junit.jupiter.api.Assertions.*;
import static com.first.genProject.support.FeedbackFixtures.VALID_FEEDBACK;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.ai.chat.client.ChatClient;

import com.first.genProject.dto.InterviewAnswerRequest;
import com.first.genProject.exception.AiProviderException;
import com.first.genProject.exception.InvalidInputException;
import com.first.genProject.support.StubChatModel;

class InterviewFeedbackServiceTest {
    private final StubChatModel model = new StubChatModel();
    private final InterviewFeedbackService service = new InterviewFeedbackService(ChatClient.create(model));
    private final InterviewAnswerRequest request = new InterviewAnswerRequest("Java", "Spring Boot", "intermediate",
            "Explain dependency injection", "public void run() { /* {format} */ }");

    @Test
    void parsesTypedFeedbackAndCalculatesTotalFromRubric() {
        model.response = VALID_FEEDBACK;
        var feedback = service.evaluateAnswer(request);
        assertEquals(7, feedback.score());
        assertEquals(10, feedback.maxScore());
        assertEquals(4, feedback.rubric().correctness().score());
        assertEquals("Identifies dependency injection", feedback.correctPoints().getFirst());
        assertEquals("Explain constructor injection", feedback.missingPoints().getFirst());
        assertTrue(model.prompt.getUserMessage().getText().contains(request.answer()));
        assertTrue(model.prompt.getUserMessage().getText().contains(request.question()));
        assertTrue(model.prompt.getSystemMessage().getText().contains("correctness (0-5)"));
        assertTrue(model.prompt.getSystemMessage().getText().contains("properties"));
        assertFalse(model.prompt.getSystemMessage().getText().contains("{format}"));
    }

    @Test
    void allowsEmptyPointListsAndZeroScores() {
        model.response = VALID_FEEDBACK.replace("[\"Identifies dependency injection\"]", "[]")
                .replace("[\"Explain constructor injection\"]", "[]")
                .replace("\"score\": 4", "\"score\": 0")
                .replace("\"score\": 2", "\"score\": 0")
                .replace("\"score\": 1", "\"score\": 0");
        assertEquals(0, service.evaluateAnswer(request).score());
    }

    static Stream<String> invalidOutputs() {
        return Stream.of(null, "", " ", "not json", "{}", "null", "[]",
                VALID_FEEDBACK.replace("\"score\": 4", "\"score\": 6"),
                VALID_FEEDBACK.replace("\"score\": 2", "\"score\": 4"),
                VALID_FEEDBACK.replace("\"score\": 1", "\"score\": 3"),
                VALID_FEEDBACK.replace("\"score\": 4", "\"score\": -1"),
                VALID_FEEDBACK.replace("\"score\": 4", "\"score\": null"),
                VALID_FEEDBACK.replace("\"score\": 4", "\"score\": 2.5"),
                VALID_FEEDBACK.replace("[\"Identifies dependency injection\"]", "null"),
                VALID_FEEDBACK.replace("[\"Explain constructor injection\"]", "[null]"),
                VALID_FEEDBACK.replace("Minor technical imprecision", " "),
                VALID_FEEDBACK.replace("Spring supplies dependencies; constructor injection makes them explicit.", " "));
    }

    @ParameterizedTest
    @MethodSource("invalidOutputs")
    void rejectsMalformedOrInvalidModelFeedback(String output) {
        model.response = output;
        assertThrows(AiProviderException.class, () -> service.evaluateAnswer(request));
    }

    @Test
    void rejectsInvalidDirectInputBeforeModelCall() {
        assertThrows(InvalidInputException.class, () -> service.evaluateAnswer(null));
        assertThrows(InvalidInputException.class, () -> service.evaluateAnswer(
                new InterviewAnswerRequest("Java", "Spring Boot", "intermediate", " ", "answer")));
        assertThrows(InvalidInputException.class, () -> service.evaluateAnswer(
                new InterviewAnswerRequest("Java", "Spring Boot", "intermediate", "q".repeat(2001), "answer")));
        assertThrows(InvalidInputException.class, () -> service.evaluateAnswer(
                new InterviewAnswerRequest("Java", "Spring Boot", "intermediate", "question", "a".repeat(8001))));
        assertEquals(0, model.calls);
    }

    @Test
    void wrapsProviderFailure() {
        model.failure = new IllegalStateException("provider details");
        assertThrows(AiProviderException.class, () -> service.evaluateAnswer(request));
    }
}
