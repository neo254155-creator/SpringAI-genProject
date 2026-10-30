package com.first.genProject.resume;

import java.util.List;
import java.util.UUID;

/** Immutable confirmed skill snapshot; later sessions cannot change it. */
public record ResumeInterviewContext(UUID resumeId, String filename, List<ResumeSkill> skills) { }
