package com.first.genProject.service;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.first.genProject.dto.CriterionScore;
import com.first.genProject.dto.FeedbackEvaluation;
import com.first.genProject.dto.InterviewAnswerRequest;
import com.first.genProject.dto.InterviewFeedback;
import com.first.genProject.exception.AiProviderException;
import com.first.genProject.exception.InvalidInputException;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Service
public class InterviewFeedbackService {
    private final ChatClient chatClient;
    private final PromptTemplate userTemplate;
    private final SystemPromptTemplate systemTemplate;
    private final BeanOutputConverter<FeedbackEvaluation> converter;

    public InterviewFeedbackService(ChatClient chatClient) {
        this.chatClient = chatClient;
        this.userTemplate = new PromptTemplate(new ClassPathResource("Prompt/InterviewFeedbackUser"));
        this.systemTemplate = new SystemPromptTemplate(new ClassPathResource("Prompt/InterviewFeedbackSystem"));
        this.converter = new BeanOutputConverter<>(FeedbackEvaluation.class,
                JsonMapper.builder().disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT).build());
    }

    public InterviewFeedback evaluateAnswer(InterviewAnswerRequest request) {
        if (request == null) {
            throw new InvalidInputException("Answer request is required");
        }
        request.validate();
        var system = systemTemplate.createMessage(Map.of("format", converter.getFormat()));
        var user = userTemplate.createMessage(Map.of(
                "track", request.track(), "topic", request.topic(), "level", request.level(),
                "question", request.question().trim(), "answer", request.answer().trim()));
        final FeedbackEvaluation evaluation;
        try {
            String output = chatClient.prompt(new Prompt(system, user)).call().content();
            if (output == null || output.isBlank()) {
                throw new AiProviderException();
            }
            evaluation = converter.convert(output);
        } catch (RuntimeException exception) {
            throw new AiProviderException(exception);
        }
        if (evaluation == null || evaluation.rubric() == null
                || !validPoints(evaluation.correctPoints()) || !validPoints(evaluation.missingPoints())
                || !hasText(evaluation.explanation())) {
            throw new AiProviderException();
        }
        var rubric = evaluation.rubric();
        requireScore(rubric.correctness(), 5);
        requireScore(rubric.completeness(), 3);
        requireScore(rubric.clarity(), 2);
        int score = rubric.correctness().score() + rubric.completeness().score() + rubric.clarity().score();
        return new InterviewFeedback(List.copyOf(evaluation.correctPoints()),
                List.copyOf(evaluation.missingPoints()), evaluation.explanation(), rubric, score, 10);
    }

    private static boolean validPoints(List<String> points) {
        return points != null && points.stream().allMatch(InterviewFeedbackService::hasText);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static void requireScore(CriterionScore criterion, int maximum) {
        if (criterion == null || criterion.score() == null || criterion.score() < 0
                || criterion.score() > maximum || !hasText(criterion.reason())) {
            throw new AiProviderException();
        }
    }
}
