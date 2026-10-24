package com.first.genProject.dto;

import java.util.List;

public record InterviewFeedback(List<String> correctPoints, List<String> missingPoints,
        String explanation, RubricScores rubric, int score, int maxScore) {
}
