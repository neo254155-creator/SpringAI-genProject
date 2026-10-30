package com.first.genProject.persistence;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name = "study_documents")
public class StudyDocumentEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, length = 200)
    private String title;
    @Column(nullable = false, length = 255)
    private String filename;
    @Column(nullable = false)
    private Instant createdAt;

    protected StudyDocumentEntity() { }

    public StudyDocumentEntity(String title, String filename) {
        this.title = title;
        this.filename = filename;
        this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public UUID getId() { return id; }
    public String getTitle() { return title; }
    public String getFilename() { return filename; }
    public Instant getCreatedAt() { return createdAt; }
}
