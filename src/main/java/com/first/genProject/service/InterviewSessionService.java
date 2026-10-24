package com.first.genProject.service;

import java.util.UUID;
import org.springframework.stereotype.Service;
import com.first.genProject.dto.*;
import com.first.genProject.exception.*;

/** Orchestrates AI outside database transactions; only completed results are stored. */
@Service
public class InterviewSessionService {
    private final InterviewHistoryStore store;
    private final InterviewService questionService;
    private final InterviewFeedbackService feedbackService;

    public InterviewSessionService(InterviewHistoryStore store, InterviewService questionService,
            InterviewFeedbackService feedbackService) {
        this.store = store;
        this.questionService = questionService;
        this.feedbackService = feedbackService;
    }

    public SessionResponse create(InterviewSelection selection) {
        if (selection == null) throw new InvalidInputException("Interview selection is required");
        selection.validate();
        return store.create(selection);
    }

    public StoredQuestionResponse generateQuestion(UUID sessionId) {
        var session = store.session(sessionId);
        if (!"ACTIVE".equals(session.status())) throw new InterviewStateException();
        var generated = questionService.generateQuestion(
                new InterviewSelection(session.track(), session.topic(), session.level()));
        if (generated.question().length() > 2000) throw new AiProviderException();
        return store.saveQuestion(sessionId, generated.question());
    }

    public StoredAnswerResponse submitAnswer(UUID questionId, AnswerSubmission submission) {
        if (submission == null) throw new InvalidInputException("Answer submission is required");
        submission.validate();
        var context = store.questionContext(questionId);
        var session = context.session();
        String answer = submission.answer().trim();
        var result = feedbackService.evaluateAnswer(new InterviewAnswerRequest(
                session.track(), session.topic(), session.level(), context.question().question(), answer));
        return store.saveAnswer(questionId, answer, result);
    }
}
