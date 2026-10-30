package com.first.genProject.persistence;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import jakarta.persistence.*;
import com.first.genProject.dto.QuestionFocus;

@Entity
@Table(name = "interview_questions")
public class InterviewQuestionEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private InterviewSessionEntity session;
    @Column(name = "question_text", nullable = false, columnDefinition = "text")
    private String question;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(length = 32)
    private String focusCriterion;
    @Column(length = 500)
    private String focusReason;
    @Column(name = "resume_skill", length = 80)
    private String resumeSkill;

    protected InterviewQuestionEntity() { }

    public InterviewQuestionEntity(InterviewSessionEntity session, String question) {
        this(session, question, null);
    }

    public InterviewQuestionEntity(InterviewSessionEntity session, String question, QuestionFocus focus) {
        this.session = session;
        this.question = question;
        this.focusCriterion = focus == null ? null : focus.criterion();
        this.focusReason = focus == null ? null : focus.reason();
        createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public UUID getId() { return id; }
    public void setResumeSkill(String skill) { this.resumeSkill = skill; }
    public String getResumeSkill() { return resumeSkill; }
    public InterviewSessionEntity getSession() { return session; }
    public String getQuestion() { return question; }
    public Instant getCreatedAt() { return createdAt; }
    public QuestionFocus getFocus() { return focusCriterion == null ? null : new QuestionFocus(focusCriterion, focusReason); }
}
