package com.first.genProject.persistence;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name = "document_search_indexes")
public class DocumentSearchIndexEntity {
    @Id
    private UUID documentId;
    @Column(nullable = false, length = 512)
    private String modelKey;
    @Column(nullable = false)
    private int dimensions;
    @Column(nullable = false, columnDefinition = "text")
    private String vectorsJson;
    @Column(nullable = false)
    private Instant indexedAt;

    protected DocumentSearchIndexEntity() { }

    public DocumentSearchIndexEntity(UUID documentId, String modelKey, int dimensions, String vectorsJson) {
        this.documentId = documentId;
        this.modelKey = modelKey;
        this.dimensions = dimensions;
        this.vectorsJson = vectorsJson;
        this.indexedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public UUID getDocumentId() { return documentId; }
    public String getModelKey() { return modelKey; }
    public int getDimensions() { return dimensions; }
    public String getVectorsJson() { return vectorsJson; }
    public Instant getIndexedAt() { return indexedAt; }
}
