package com.first.genProject.embedding;

import java.util.*;
import com.first.genProject.service.AiQuota;

/** Bounded batches; each outbound HTTP attempt reserves shared quota. */
public class QuotaEmbeddingClient implements PassageEmbeddingClient {
    private final PassageEmbeddingClient delegate;
    private final AiQuota quota;
    public QuotaEmbeddingClient(PassageEmbeddingClient delegate, AiQuota quota) { this.delegate=delegate; this.quota=quota; }
    public String modelKey() { return delegate.modelKey(); }
    public List<double[]> embed(List<String> texts) {
        if (texts==null || texts.isEmpty() || texts.size()>64 || texts.stream().anyMatch(t -> t==null || t.isBlank() || t.length()>2000))
            throw new com.first.genProject.exception.InvalidInputException("Embedding input requires 1–64 texts, each at most 2000 characters.");
        var result=new ArrayList<double[]>();
        for (int from=0; from<texts.size();) {
            int end=from, size=0;
            while (end<texts.size() && end-from<16 && size+texts.get(end).length()<=20000) size+=texts.get(end++).length();
            var batch=List.copyOf(texts.subList(from,end));
            result.addAll(quota.call(size, () -> delegate.embed(batch)));
            from=end;
        }
        return List.copyOf(result);
    }
}
