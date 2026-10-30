package com.first.genProject.resume;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.json.JsonMapper;

@Entity
@Table(name = "resume_profiles")
public class ResumeEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false, length = 255) private String filename;
    @Column(name = "skills_json", nullable = false, columnDefinition = "TEXT") private String skillsJson;
    @Column(nullable = false) private Instant createdAt;
    protected ResumeEntity() { }
    public ResumeEntity(String filename, List<ResumeSkill> skills) {
        this.filename = filename;
        skillsJson = JsonMapper.builder().build().writeValueAsString(skills);
        createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
    public ResumeProfile view() {
        return new ResumeProfile(id, filename, createdAt,
                List.of(JsonMapper.builder().build().readValue(skillsJson, ResumeSkill[].class)));
    }
}
