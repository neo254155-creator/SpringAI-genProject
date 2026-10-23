package com.first.genProject.dto;

import java.util.List;
import com.first.genProject.exception.InvalidInputException;

public record InterviewSelection(String track, String topic, String level) {
    public static final List<String> TRACKS = List.of("Java");
    public static final List<String> TOPICS = List.of("Core Java", "Spring Boot");
    public static final List<String> LEVELS = List.of("beginner", "intermediate", "advanced");

    public void validate() {
        if (track == null || !TRACKS.contains(track)) {
            throw new InvalidInputException("Choose a supported track: " + TRACKS);
        }
        if (topic == null || !TOPICS.contains(topic)) {
            throw new InvalidInputException("Choose a supported topic: " + TOPICS);
        }
        if (level == null || !LEVELS.contains(level)) {
            throw new InvalidInputException("Choose a supported level: " + LEVELS);
        }
    }
}
