package com.first.genProject.persistence;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name = "interview_answers")
public class InterviewAnswerEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private InterviewQuestionEntity question;
    @Column(name = "answer_text", nullable = false, columnDefinition = "text")
    private String answer;
    @Column(nullable = false)
    private Instant submittedAt;

    protected InterviewAnswerEntity() { }

    public InterviewAnswerEntity(InterviewQuestionEntity question, String answer) {
        this.question = question;
        this.answer = answer;
        submittedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public UUID getId() { return id; }
    public InterviewQuestionEntity getQuestion() { return question; }
    public String getAnswer() { return answer; }
    public Instant getSubmittedAt() { return submittedAt; }
}
