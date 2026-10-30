package com.first.genProject.dto;

import java.time.Instant;
import java.util.UUID;

public record StoredQuestionResponse(UUID id, UUID sessionId, String question, Instant createdAt, QuestionFocus focus, String skill) {
}
