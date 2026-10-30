package com.first.genProject.service;

import java.util.List;
import org.springframework.stereotype.Component;
import com.first.genProject.dto.*;

/** Targets the lowest proportional rubric score in the session's latest saved feedback. */
@Component
public class AdaptiveQuestionPolicy {
    public Plan plan(InterviewFeedback feedback, List<String> recentQuestions) {
        if (feedback == null) return new Plan(null, "", List.of(), List.copyOf(recentQuestions));
        var rubric = feedback.rubric();
        var weakest = new Criterion("correctness", rubric.correctness(), 5);
        for (var candidate : List.of(new Criterion("completeness", rubric.completeness(), 3),
                new Criterion("clarity", rubric.clarity(), 2))) {
            // Cross-multiplication compares ratios exactly; ties keep the earlier criterion.
            if (candidate.value().score() * weakest.maximum() < weakest.value().score() * candidate.maximum()) {
                weakest = candidate;
            }
        }
        if (weakest.value().score() == weakest.maximum()) {
            return new Plan(null, "", List.of(), List.copyOf(recentQuestions));
        }
        var focus = new QuestionFocus(weakest.name(), "Practise " + weakest.name() + " ("
                + weakest.value().score() + "/" + weakest.maximum() + ") from your latest saved feedback.");
        return new Plan(focus, shorten(weakest.value().reason(), 300), feedback.missingPoints().stream()
                .limit(3).map(point -> shorten(point, 200)).toList(), List.copyOf(recentQuestions));
    }

    private String shorten(String value, int length) {
        int end = Math.min(value.length(), length);
        if (end < value.length() && end > 0 && Character.isHighSurrogate(value.charAt(end - 1))) end--;
        return value.substring(0, end);
    }

    private record Criterion(String name, CriterionScore value, int maximum) { }
    public record Plan(QuestionFocus focus, String rubricReason, List<String> missingPoints, List<String> recentQuestions) { }
}
