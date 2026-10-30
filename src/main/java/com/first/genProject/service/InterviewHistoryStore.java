package com.first.genProject.service;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.first.genProject.dto.*;
import com.first.genProject.exception.*;
import com.first.genProject.persistence.*;
import com.first.genProject.resume.ResumeInterviewContext;

/** Database transactions end before orchestration calls the AI provider. */
@Service
public class InterviewHistoryStore {
    private final InterviewSessionRepository sessions;
    private final InterviewQuestionRepository questions;
    private final InterviewAnswerRepository answers;
    private final InterviewFeedbackRepository feedback;

    public InterviewHistoryStore(InterviewSessionRepository sessions, InterviewQuestionRepository questions,
            InterviewAnswerRepository answers, InterviewFeedbackRepository feedback) {
        this.sessions = sessions;
        this.questions = questions;
        this.answers = answers;
        this.feedback = feedback;
    }

    @Transactional
    public SessionResponse create(InterviewSelection selection) {
        return sessionView(sessions.save(new InterviewSessionEntity(selection)));
    }

    @Transactional
    public SessionResponse createResume(ResumeInterviewContext context, String level) {
        return sessionView(sessions.save(new InterviewSessionEntity(context, level)));
    }

    @Transactional(readOnly = true)
    public SessionResponse session(UUID id) {
        return sessionView(loadSession(id));
    }

    @Transactional(readOnly = true)
    public GenerationContext generationContext(UUID id) {
        var session = loadSession(id);
        requireActive(session);
        var recent = questions.findBySession_Id(id, pageRequest(0, 5, "createdAt")).stream()
                .map(InterviewQuestionEntity::getQuestion).toList();
        var latest = feedback.findByAnswer_Question_Session_Id(id, pageRequest(0, 1, "answer.submittedAt"))
                .stream().findFirst();
        return new GenerationContext(sessionView(session), recent,
                latest.map(InterviewFeedbackEntity::toFeedback).orElse(null),
                latest.map(f -> f.getAnswer().getQuestion().getResumeSkill()).orElse(null),
                questions.countBySession_Id(id));
    }

    @Transactional(readOnly = true)
    public SessionResumeResponse resume(UUID id) {
        var session = loadSession(id);
        var latest = questions.findBySession_Id(id, pageRequest(0, 1, "createdAt")).stream().findFirst();
        StoredQuestionResponse question = latest.map(this::questionView).orElse(null);
        StoredAnswerResponse answer = latest.flatMap(q -> feedback.findByAnswer_Question_Id(q.getId(),
                pageRequest(0, 1, "answer.submittedAt")).stream().findFirst()).map(this::answerView).orElse(null);
        return new SessionResumeResponse(sessionView(session), question, answer, feedback.countQuestionsAnswered(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<SessionResponse> sessions(int page, int size) {
        return pageView(sessions.findAll(pageRequest(page, size, "createdAt")).map(this::sessionView));
    }

    @Transactional(readOnly = true)
    public PageResponse<StoredQuestionResponse> questions(UUID sessionId, int page, int size) {
        loadSession(sessionId);
        return pageView(questions.findBySession_Id(sessionId, pageRequest(page, size, "createdAt"))
                .map(this::questionView));
    }

    @Transactional(readOnly = true)
    public QuestionContext questionContext(UUID questionId) {
        var question = loadQuestion(questionId);
        requireActive(question.getSession());
        return new QuestionContext(sessionView(question.getSession()), questionView(question));
    }

    @Transactional
    public StoredQuestionResponse saveQuestion(UUID sessionId, String question) {
        return saveQuestion(sessionId, question, null);
    }

    @Transactional
    public StoredQuestionResponse saveQuestion(UUID sessionId, String question, QuestionFocus focus) {
        return saveQuestion(sessionId, question, focus, null);
    }

    @Transactional
    public StoredQuestionResponse saveQuestion(UUID sessionId, String question, QuestionFocus focus, String skill) {
        var session = lockSession(sessionId);
        requireActive(session);
        if (session.getResume() != null && (skill == null || session.getResume().skills().stream()
                .noneMatch(s -> s.name().equals(skill)))) throw new InvalidInputException("Question skill must belong to the confirmed resume.");
        var entity = new InterviewQuestionEntity(session, question, focus);
        entity.setResumeSkill(skill);
        return questionView(questions.save(entity));
    }

    @Transactional
    public StoredAnswerResponse saveAnswer(UUID questionId, String text, InterviewFeedback evaluation) {
        var question = loadQuestion(questionId);
        requireActive(lockSession(question.getSession().getId()));
        var answer = answers.save(new InterviewAnswerEntity(question, text));
        // A failure inserting feedback rolls back the answer as well.
        return answerView(feedback.save(new InterviewFeedbackEntity(answer, evaluation)));
    }

    @Transactional(readOnly = true)
    public PageResponse<StoredAnswerResponse> answers(UUID questionId, int page, int size) {
        loadQuestion(questionId);
        return pageView(feedback.findByAnswer_Question_Id(questionId,
                pageRequest(page, size, "answer.submittedAt")).map(this::answerView));
    }

    @Transactional
    public SessionResponse complete(UUID id) {
        var session = lockSession(id);
        session.complete();
        return sessionView(session);
    }

    @Transactional(readOnly = true)
    public List<TopicProgress> progress() { return feedback.topicProgress(); }

    private InterviewSessionEntity loadSession(UUID id) {
        return sessions.findById(id).orElseThrow(() -> new InterviewNotFoundException("Session"));
    }

    private InterviewSessionEntity lockSession(UUID id) {
        return sessions.findForUpdate(id).orElseThrow(() -> new InterviewNotFoundException("Session"));
    }

    private InterviewQuestionEntity loadQuestion(UUID id) {
        return questions.findById(id).orElseThrow(() -> new InterviewNotFoundException("Question"));
    }

    private void requireActive(InterviewSessionEntity session) {
        if (!session.isActive()) throw new InterviewStateException();
    }

    private PageRequest pageRequest(int page, int size, String sortProperty) {
        if (page < 0 || size < 1 || size > 100) {
            throw new InvalidInputException("page must be nonnegative and size must be between 1 and 100");
        }
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, sortProperty, "id"));
    }

    private <T> PageResponse<T> pageView(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    private SessionResponse sessionView(InterviewSessionEntity session) {
        return new SessionResponse(session.getId(), session.getTrack(), session.getTopic(),
                session.getDifficulty(), session.getStatus(), session.getCreatedAt(), session.getCompletedAt(), session.getResume());
    }

    private StoredQuestionResponse questionView(InterviewQuestionEntity question) {
        return new StoredQuestionResponse(question.getId(), question.getSession().getId(),
                question.getQuestion(), question.getCreatedAt(), question.getFocus(), question.getResumeSkill());
    }

    private StoredAnswerResponse answerView(InterviewFeedbackEntity result) {
        var answer = result.getAnswer();
        return new StoredAnswerResponse(answer.getId(), answer.getQuestion().getId(), answer.getAnswer(),
                answer.getSubmittedAt(), result.getRubricVersion(), result.toFeedback());
    }

    public record QuestionContext(SessionResponse session, StoredQuestionResponse question) { }
    public record GenerationContext(SessionResponse session, List<String> recentQuestions,
            InterviewFeedback latestFeedback, String latestSkill, long questionCount) { }
}
