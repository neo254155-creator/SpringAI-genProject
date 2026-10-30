package com.first.genProject.service;

import java.util.UUID;
import java.util.Locale;
import org.springframework.stereotype.Service;
import com.first.genProject.dto.*;
import com.first.genProject.exception.*;

/** Orchestrates AI outside database transactions; only completed results are stored. */
@Service
public class InterviewSessionService {
    private final InterviewHistoryStore store;
    private final InterviewService questionService;
    private final InterviewFeedbackService feedbackService;
    private final AdaptiveQuestionPolicy policy;

    public InterviewSessionService(InterviewHistoryStore store, InterviewService questionService,
            InterviewFeedbackService feedbackService, AdaptiveQuestionPolicy policy) {
        this.store = store;
        this.questionService = questionService;
        this.feedbackService = feedbackService;
        this.policy = policy;
    }

    public SessionResponse create(InterviewSelection selection) {
        if (selection == null) throw new InvalidInputException("Interview selection is required");
        selection.validate();
        return store.create(selection);
    }

    public StoredQuestionResponse generateQuestion(UUID sessionId) {
        var context = store.generationContext(sessionId);
        var session = context.session();
        var plan = policy.plan(context.latestFeedback(), context.recentQuestions());
        var selection = new InterviewSelection(session.track(), session.topic(), session.level());
        String skillName = null;
        InterviewQuestion generated;
        if (session.resume() != null) {
            var skills = session.resume().skills();
            int index = (int) (context.questionCount() % skills.size());
            if (context.latestSkill() != null) {
                for (int i = 0; i < skills.size(); i++) {
                    if (skills.get(i).name().equals(context.latestSkill())) {
                        index = plan.focus() != null ? i : (i + 1) % skills.size();
                        break;
                    }
                }
            }
            var chosen = skills.get(index);
            skillName = chosen.name();
            generated = questionService.generateResumeQuestion(chosen, session.level(), plan);
        } else {
            generated = context.recentQuestions().isEmpty() && plan.focus() == null
                    ? questionService.generateQuestion(selection) : questionService.generateFollowUp(selection, plan);
        }
        if (generated.question().length() > 2000) throw new AiProviderException();
        if (context.recentQuestions().stream().anyMatch(previous -> normalize(previous).equals(normalize(generated.question())))) {
            throw new AiProviderException();
        }
        return store.saveQuestion(sessionId, generated.question(), plan.focus(), skillName);
    }

    private String normalize(String question) { return question.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT); }

    public StoredAnswerResponse submitAnswer(UUID questionId, AnswerSubmission submission) {
        if (submission == null) throw new InvalidInputException("Answer submission is required");
        submission.validate();
        var context = store.questionContext(questionId);
        var session = context.session();
        String answer = submission.answer().trim();
        var result = session.resume() == null ? feedbackService.evaluateAnswer(new InterviewAnswerRequest(
                session.track(), session.topic(), session.level(), context.question().question(), answer))
                : feedbackService.evaluateResumeAnswer(context.question().skill(), session.level(), context.question().question(), answer);
        return store.saveAnswer(questionId, answer, result);
    }
}
