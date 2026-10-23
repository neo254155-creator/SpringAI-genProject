package com.first.genProject.dto;

import java.util.List;

public record InterviewSelection(String track, String topic, String level) {
    public static final List<String> TRACKS = List.of("Java");
    public static final List<String> TOPICS = List.of("Core Java", "Spring Boot");
    public static final List<String> LEVELS = List.of("beginner", "intermediate", "advanced");

    public void validate() {
        if (track == null || !TRACKS.contains(track)) {
            throw new IllegalArgumentException("Choose a supported track: " + TRACKS);
        }
        if (topic == null || !TOPICS.contains(topic)) {
            throw new IllegalArgumentException("Choose a supported topic: " + TOPICS);
        }
        if (level == null || !LEVELS.contains(level)) {
            throw new IllegalArgumentException("Choose a supported level: " + LEVELS);
        }
    }
}
