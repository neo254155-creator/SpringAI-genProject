package com.first.genProject.dto;

import java.time.Instant;
import java.util.UUID;

public record DocumentIndexResponse(UUID documentId, String model, int dimensions,
        int passageCount, Instant indexedAt) { }
