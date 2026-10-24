package com.first.genProject.persistence;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import jakarta.persistence.*;
import com.first.genProject.dto.InterviewSelection;

@Entity
@Table(name = "interview_sessions")
public class InterviewSessionEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, length = 100)
    private String track;
    @Column(nullable = false, length = 200)
    private String topic;
    @Column(nullable = false, length = 50)
    private String difficulty;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(nullable = false)
    private Instant createdAt;
    private Instant completedAt;
    @Version
    private long version;

    protected InterviewSessionEntity() { }

    public InterviewSessionEntity(InterviewSelection selection) {
        track = selection.track();
        topic = selection.topic();
        difficulty = selection.level();
        status = "ACTIVE";
        createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public void complete() {
        if (isActive()) {
            status = "COMPLETED";
            completedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        }
    }

    public boolean isActive() { return "ACTIVE".equals(status); }
    public UUID getId() { return id; }
    public String getTrack() { return track; }
    public String getTopic() { return topic; }
    public String getDifficulty() { return difficulty; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
}
