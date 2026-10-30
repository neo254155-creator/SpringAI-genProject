package com.first.genProject.dto;

import com.first.genProject.exception.InvalidInputException;

public record DocumentSearchRequest(String query, Integer limit, Double minimumScore) {
    public DocumentSearchRequest validated() {
        if (query == null || query.isBlank() || query.length() > 2000 || query.indexOf('\0') >= 0) {
            throw new InvalidInputException("query must contain between 1 and 2000 characters");
        }
        int count = limit == null ? 5 : limit;
        double score = minimumScore == null ? 0 : minimumScore;
        if (count < 1 || count > 20 || !Double.isFinite(score) || score < -1 || score > 1) {
            throw new InvalidInputException("limit must be between 1 and 20 and minimumScore between -1 and 1");
        }
        return new DocumentSearchRequest(query.strip(), count, score);
    }
}
