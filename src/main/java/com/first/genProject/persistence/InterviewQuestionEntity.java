package com.first.genProject.persistence;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import jakarta.persistence.*;

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

    protected InterviewQuestionEntity() { }

    public InterviewQuestionEntity(InterviewSessionEntity session, String question) {
        this.session = session;
        this.question = question;
        createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public UUID getId() { return id; }
    public InterviewSessionEntity getSession() { return session; }
    public String getQuestion() { return question; }
    public Instant getCreatedAt() { return createdAt; }
}
