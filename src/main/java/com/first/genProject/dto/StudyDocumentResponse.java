package com.first.genProject.dto;

import java.time.Instant;
import java.util.UUID;

public record StudyDocumentResponse(UUID id, String title, String filename, Instant createdAt) { }
