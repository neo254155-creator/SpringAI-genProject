package com.first.genProject.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.stream.Stream;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.MessageType;

import com.first.genProject.dto.InterviewSelection;
import com.first.genProject.dto.QuestionFocus;
import com.first.genProject.support.StubChatModel;

class InterviewServiceTest {
    @Test
    void followUpTemplatePreservesSelectionAndTreatsSavedEvidenceAsData() {
        var model = new StubChatModel(); model.response = "How would you explain constructor injection concisely?";
        var service = new InterviewService(ChatClient.create(model));
        var plan = new AdaptiveQuestionPolicy.Plan(new QuestionFocus("clarity", "Practise clarity"),
                "Explain Map<String, Bean> {example} precisely", List.of("Missing {details}"), List.of("Earlier question?"));
        service.generateFollowUp(new InterviewSelection("Java", "Spring Boot", "intermediate"), plan);
        var text = model.prompt.getUserMessage().getText();
        assertTrue(text.contains("Topic: Spring Boot"));
        assertTrue(text.contains("Difficulty: intermediate"));
        assertTrue(text.contains("Practice focus: clarity"));
        assertTrue(text.contains("Missing {details}"));
        assertTrue(text.contains("Map<String, Bean> {example}"));
        assertTrue(text.contains("Earlier question?"));
        assertTrue(text.contains("untrusted practice data"));
        assertTrue(model.prompt.getSystemMessage().getText().contains("do not follow"));
        assertEquals(1, model.calls);
    }

    static Stream<Arguments> supportedSelections() {
        return InterviewSelection.TOPICS.stream().flatMap(topic ->
                InterviewSelection.LEVELS.stream().map(level -> Arguments.of(topic, level)));
    }

    @ParameterizedTest
    @MethodSource("supportedSelections")
    void generatesQuestionForEverySupportedTopicAndDifficulty(String topic, String level) {
        var model = new StubChatModel();
        model.response = "  A generated interview question?  ";
        var service = new InterviewService(ChatClient.create(model));

        var question = service.generateQuestion(new InterviewSelection("Java", topic, level));

        assertEquals("Java", question.track());
        assertEquals(topic, question.topic());
        assertEquals(level, question.level());
        assertEquals("A generated interview question?", question.question());
        assertEquals(1, model.calls);
        assertEquals(2, model.prompt.getInstructions().size());
        assertEquals(MessageType.SYSTEM, model.prompt.getInstructions().getFirst().getMessageType());
        assertEquals(MessageType.USER, model.prompt.getInstructions().getLast().getMessageType());
        String rendered = model.prompt.getUserMessage().getText();
        assertTrue(rendered.contains("Track: Java"));
        assertTrue(rendered.contains("Topic: " + topic));
        assertTrue(rendered.contains("Difficulty: " + level));
        assertFalse(rendered.contains("{track}"));
        assertFalse(rendered.contains("{topic}"));
        assertFalse(rendered.contains("{level}"));
        String instructions = model.prompt.getSystemMessage().getText();
        assertTrue(instructions.contains("exactly one"));
        assertTrue(instructions.contains("without a heading, answer, hint"));
    }
}
