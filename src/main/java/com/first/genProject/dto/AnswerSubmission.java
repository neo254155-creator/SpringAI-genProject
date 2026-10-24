package com.first.genProject.dto;

import com.first.genProject.exception.InvalidInputException;

public record AnswerSubmission(String answer) {
    public void validate() {
        if (answer == null || answer.isBlank() || answer.length() > 8000) {
            throw new InvalidInputException("answer must be nonblank and at most 8000 characters");
        }
    }
}
