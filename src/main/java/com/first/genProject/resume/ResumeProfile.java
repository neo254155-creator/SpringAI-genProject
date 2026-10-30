package com.first.genProject.resume;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ResumeProfile(UUID id, String filename, Instant createdAt, List<ResumeSkill> skills) { }
