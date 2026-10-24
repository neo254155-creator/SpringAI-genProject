package com.first.genProject.dto;

import com.first.genProject.exception.InvalidInputException;

public record InterviewAnswerRequest(String track, String topic, String level,
        String question, String answer) {
    public void validate() {
        new InterviewSelection(track, topic, level).validate();
        requireText("question", question, 2000);
        requireText("answer", answer, 8000);
    }

    private static void requireText(String name, String value, int limit) {
        if (value == null || value.isBlank()) {
            throw new InvalidInputException(name + " must not be blank");
        }
        if (value.length() > limit) {
            throw new InvalidInputException(name + " must be at most " + limit + " characters");
        }
    }
}
