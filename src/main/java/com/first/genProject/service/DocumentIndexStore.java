package com.first.genProject.service;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import com.first.genProject.dto.DocumentIndexResponse;
import com.first.genProject.exception.*;
import com.first.genProject.persistence.*;

@Service
public class DocumentIndexStore {
    private final StudyDocumentRepository documents;
    private final DocumentSearchIndexRepository indexes;
    private final JsonMapper json = JsonMapper.builder().build();
    private final com.first.genProject.auth.CurrentAccount current;

    public DocumentIndexStore(StudyDocumentRepository documents, DocumentSearchIndexRepository indexes, com.first.genProject.auth.CurrentAccount current) {
        this.documents = documents;
        this.indexes = indexes;
        this.current = current;
    }

    @Transactional
    public DocumentIndexResponse replace(UUID id, String modelKey, List<PassageVector> vectors) {
        documents.findOwnedForUpdate(id, current.id()).orElseThrow(() -> new InterviewNotFoundException("Document"));
        var index = indexes.save(new DocumentSearchIndexEntity(id, modelKey,
                vectors.getFirst().vector().length, json.writeValueAsString(vectors)));
        return new DocumentIndexResponse(id, modelKey, index.getDimensions(), vectors.size(), index.getIndexedAt());
    }

    @Transactional(readOnly = true)
    public IndexSnapshot load(UUID id) {
        documents.findByIdAndOwnerId(id, current.id()).orElseThrow(() -> new InterviewNotFoundException("Document"));
        var index = indexes.findById(id).orElseThrow(() ->
                new DocumentSearchException(DocumentSearchException.Reason.INDEX_NOT_READY));
        return new IndexSnapshot(index.getModelKey(), index.getDimensions(),
                List.of(json.readValue(index.getVectorsJson(), PassageVector[].class)));
    }

    public record PassageVector(UUID passageId, double[] vector) { }
    public record IndexSnapshot(String modelKey, int dimensions, List<PassageVector> vectors) { }
}
