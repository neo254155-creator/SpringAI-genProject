package com.first.genProject.service;

import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.first.genProject.exception.AiProviderException;
import com.first.genProject.exception.InvalidInputException;
import com.first.genProject.dto.InterviewQuestion;
import com.first.genProject.dto.InterviewSelection;

@Service
public class InterviewService {
    private final ChatClient chatClient;
    private final PromptTemplate questionTemplate;

    public InterviewService(ChatClient chatClient) {
        this.chatClient = chatClient;
        this.questionTemplate = new PromptTemplate(new ClassPathResource("Prompt/InterviewQuestion"));
    }

    public InterviewQuestion generateQuestion(InterviewSelection selection) {
        if (selection == null) {
            throw new InvalidInputException("Interview selection is required");
        }
        selection.validate();
        var userMessage = questionTemplate.createMessage(Map.of(
                "track", selection.track(), "topic", selection.topic(), "level", selection.level()));
        var prompt = new Prompt(new SystemMessage("""
                You are a technical interviewer. Ask exactly one focused interview question.
                Return only the question text, without a heading, answer, hint, explanation,
                scoring rubric, or additional questions. Use plain text.
                """), userMessage);
        final String question;
        try {
            question = chatClient.prompt(prompt)
                    .call().content();
        } catch (RuntimeException exception) {
            throw new AiProviderException(exception);
        }
        if (question == null || question.isBlank()) {
            throw new AiProviderException();
        }
        return new InterviewQuestion(selection.track(), selection.topic(), selection.level(), question.trim());
    }
}
