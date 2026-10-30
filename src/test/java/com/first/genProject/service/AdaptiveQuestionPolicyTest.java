package com.first.genProject.service;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import com.first.genProject.dto.*;

class AdaptiveQuestionPolicyTest {
    private final AdaptiveQuestionPolicy policy = new AdaptiveQuestionPolicy();

    static Stream<Arguments> rubricCases() {
        return Stream.of(Arguments.of(1, 3, 2, "correctness"), Arguments.of(5, 1, 2, "completeness"),
                Arguments.of(4, 2, 1, "clarity"), Arguments.of(0, 0, 0, "correctness"),
                Arguments.of(5, 0, 0, "completeness"));
    }

    @ParameterizedTest @MethodSource("rubricCases")
    void choosesLowestProportionalScoreWithStableTieOrder(int correctness, int completeness, int clarity, String expected) {
        var plan = policy.plan(feedback(correctness, completeness, clarity), List.of("Previous question?"));
        assertEquals(expected, plan.focus().criterion());
        assertTrue(plan.focus().reason().contains("latest saved feedback"));
        assertEquals(List.of("Explain constructor injection"), plan.missingPoints());
        assertEquals(List.of("Previous question?"), plan.recentQuestions());
    }

    @Test
    void noFeedbackOrPerfectScoresDoNotInventAWeakness() {
        assertNull(policy.plan(null, List.of()).focus());
        var plan = policy.plan(feedback(5, 3, 2), List.of("An earlier question?"));
        assertNull(plan.focus());
        assertTrue(plan.missingPoints().isEmpty());
        assertEquals(List.of("An earlier question?"), plan.recentQuestions());
    }

    @Test
    void capsPromptEvidenceWithoutSplittingUnicodePairs() {
        var result = new InterviewFeedback(List.of(), List.of("x".repeat(199) + "😀", "two", "three", "four"), "Explanation",
                new RubricScores(new CriterionScore(0, "x".repeat(299) + "😀"), new CriterionScore(3, "Good"),
                        new CriterionScore(2, "Good")), 5, 10);
        var plan = policy.plan(result, List.of());
        assertEquals(3, plan.missingPoints().size());
        assertEquals(199, plan.missingPoints().getFirst().length());
        assertEquals(299, plan.rubricReason().length());
    }

    private InterviewFeedback feedback(int correctness, int completeness, int clarity) {
        return new InterviewFeedback(List.of("A correct point"), List.of("Explain constructor injection"), "Explanation",
                new RubricScores(new CriterionScore(correctness, "Technical reason"), new CriterionScore(completeness, "Coverage reason"),
                        new CriterionScore(clarity, "Clarity reason")), correctness + completeness + clarity, 10);
    }
}
