package com.first.genProject.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.*;
import com.first.genProject.dto.*;

@Entity
@Table(name = "interview_feedback")
public class InterviewFeedbackEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "answer_id", nullable = false, unique = true)
    private InterviewAnswerEntity answer;
    @Column(nullable = false, columnDefinition = "text")
    private String explanation;
    @Column(nullable = false)
    private int correctness;
    @Column(nullable = false, columnDefinition = "text")
    private String correctnessReason;
    @Column(nullable = false)
    private int completeness;
    @Column(nullable = false, columnDefinition = "text")
    private String completenessReason;
    @Column(nullable = false)
    private int clarity;
    @Column(nullable = false, columnDefinition = "text")
    private String clarityReason;
    @Column(nullable = false)
    private int score;
    @Column(nullable = false)
    private int maxScore;
    @Column(nullable = false, length = 40)
    private String rubricVersion;
    @ElementCollection
    @CollectionTable(name = "feedback_correct_points", joinColumns = @JoinColumn(name = "feedback_id"))
    @OrderColumn(name = "point_order")
    @Column(name = "point_text", nullable = false, columnDefinition = "text")
    private List<String> correctPoints = new ArrayList<>();
    @ElementCollection
    @CollectionTable(name = "feedback_missing_points", joinColumns = @JoinColumn(name = "feedback_id"))
    @OrderColumn(name = "point_order")
    @Column(name = "point_text", nullable = false, columnDefinition = "text")
    private List<String> missingPoints = new ArrayList<>();

    protected InterviewFeedbackEntity() { }

    public InterviewFeedbackEntity(InterviewAnswerEntity answer, InterviewFeedback feedback) {
        this.answer = answer;
        explanation = feedback.explanation();
        correctness = feedback.rubric().correctness().score();
        correctnessReason = feedback.rubric().correctness().reason();
        completeness = feedback.rubric().completeness().score();
        completenessReason = feedback.rubric().completeness().reason();
        clarity = feedback.rubric().clarity().score();
        clarityReason = feedback.rubric().clarity().reason();
        score = feedback.score();
        maxScore = feedback.maxScore();
        rubricVersion = "java-interview-v1";
        correctPoints.addAll(feedback.correctPoints());
        missingPoints.addAll(feedback.missingPoints());
    }

    public InterviewAnswerEntity getAnswer() { return answer; }
    public String getRubricVersion() { return rubricVersion; }

    public InterviewFeedback toFeedback() {
        return new InterviewFeedback(List.copyOf(correctPoints), List.copyOf(missingPoints), explanation,
                new RubricScores(new CriterionScore(correctness, correctnessReason),
                        new CriterionScore(completeness, completenessReason),
                        new CriterionScore(clarity, clarityReason)), score, maxScore);
    }
}
