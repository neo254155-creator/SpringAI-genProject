package com.first.genProject.dto;

import java.util.List;
import java.util.UUID;

/** Source text is copied from retrieval, never from the model's response. */
public record DocumentAnswerResponse(UUID documentId, String title, String query,
        String answer, boolean insufficientEvidence, List<DocumentSearchResponse.Match> sources) { }
