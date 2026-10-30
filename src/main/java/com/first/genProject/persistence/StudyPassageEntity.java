package com.first.genProject.persistence;

import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name = "study_passages")
public class StudyPassageEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private StudyDocumentEntity document;
    @Column(nullable = false)
    private int passageNumber;
    @Column(nullable = false, columnDefinition = "text")
    private String passageText;

    protected StudyPassageEntity() { }

    public StudyPassageEntity(StudyDocumentEntity document, int number, String text) {
        this.document = document;
        this.passageNumber = number;
        this.passageText = text;
    }

    public UUID getId() { return id; }
    public int getPassageNumber() { return passageNumber; }
    public String getPassageText() { return passageText; }
}
