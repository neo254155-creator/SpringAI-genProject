package com.first.genProject.dto;

import java.time.Instant;
import java.util.UUID;

public record StoredAnswerResponse(UUID id, UUID questionId, String answer, Instant submittedAt,
        String rubricVersion, InterviewFeedback feedback) {
}
