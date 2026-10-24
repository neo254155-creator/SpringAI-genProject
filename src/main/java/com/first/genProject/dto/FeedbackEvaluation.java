package com.first.genProject.dto;

import java.util.List;

/** Model output before validation and calculation of the total score. */
public record FeedbackEvaluation(List<String> correctPoints, List<String> missingPoints,
        String explanation, RubricScores rubric) {
}
