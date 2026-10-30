package com.first.genProject.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import com.first.genProject.dto.*;
import com.first.genProject.embedding.*;
import com.first.genProject.exception.DocumentSearchException;
import com.first.genProject.service.DocumentIndexStore.PassageVector;
import static com.first.genProject.exception.DocumentSearchException.Reason.INDEX_NOT_READY;

/** Calls the embedding provider only after database transactions have ended. */
@Service
public class DocumentSearchService {
    private final StudyDocumentService documents;
    private final DocumentIndexStore indexes;
    private final PassageEmbeddingClient embeddings;

    public DocumentSearchService(StudyDocumentService documents, DocumentIndexStore indexes,
            PassageEmbeddingClient embeddings) {
        this.documents = documents;
        this.indexes = indexes;
        this.embeddings = embeddings;
    }

    public DocumentIndexResponse index(UUID id) {
        var passages = documents.passages(id);
        String key = embeddings.modelKey();
        var vectors = EmbeddingVectors.normalize(embeddings.embed(passages.stream()
                .map(StudyPassageResponse::text).toList()), passages.size());
        List<PassageVector> saved = new ArrayList<>();
        for (int i = 0; i < passages.size(); i++) saved.add(new PassageVector(passages.get(i).id(), vectors.get(i)));
        return indexes.replace(id, key, saved);
    }

    public DocumentSearchResponse search(UUID id, DocumentSearchRequest request) {
        request = request.validated();
        var document = documents.document(id);
        String key = embeddings.modelKey();
        var index = indexes.load(id);
        if (!index.modelKey().equals(key)) throw new DocumentSearchException(INDEX_NOT_READY);
        var passages = documents.passages(id).stream()
                .collect(Collectors.toMap(StudyPassageResponse::id, p -> p));
        if (passages.size() != index.vectors().size() || index.vectors().stream()
                .anyMatch(p -> !passages.containsKey(p.passageId()))) throw new DocumentSearchException(INDEX_NOT_READY);
        double[] query = EmbeddingVectors.normalize(embeddings.embed(List.of(request.query())), 1).getFirst();
        if (query.length != index.dimensions()) throw new DocumentSearchException(INDEX_NOT_READY);
        List<DocumentSearchResponse.Match> matches = new ArrayList<>();
        for (var vector : index.vectors()) {
            var passage = passages.get(vector.passageId());
            double score = EmbeddingVectors.cosine(query, vector.vector());
            if (score >= request.minimumScore()) {
                matches.add(new DocumentSearchResponse.Match(passage.id(), passage.number(), passage.text(), score));
            }
        }
        matches.sort(Comparator.comparingDouble(DocumentSearchResponse.Match::similarity).reversed()
                .thenComparingInt(DocumentSearchResponse.Match::number));
        return new DocumentSearchResponse(id, document.title(), request.query(),
                List.copyOf(matches.subList(0, Math.min(matches.size(), request.limit()))));
    }
}
