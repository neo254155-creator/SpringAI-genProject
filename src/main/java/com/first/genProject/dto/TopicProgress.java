package com.first.genProject.dto;

import java.time.Instant;

public record TopicProgress(String track, String topic, String level, long attempts,
        long questionsAnswered, double averageScore, int bestScore, Instant lastPracticedAt) {
}
