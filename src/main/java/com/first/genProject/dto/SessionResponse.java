package com.first.genProject.dto;

import java.time.Instant;
import java.util.UUID;

public record SessionResponse(UUID id, String track, String topic, String level, String status,
        Instant createdAt, Instant completedAt) {
}
