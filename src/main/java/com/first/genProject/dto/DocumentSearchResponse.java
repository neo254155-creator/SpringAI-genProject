package com.first.genProject.dto;

import java.util.List;
import java.util.UUID;

public record DocumentSearchResponse(UUID documentId, String title, String query, List<Match> matches) {
    public record Match(UUID passageId, int number, String text, double similarity) { }
}
