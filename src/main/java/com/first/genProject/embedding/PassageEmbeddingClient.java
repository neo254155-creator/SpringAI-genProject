package com.first.genProject.embedding;

import java.util.List;

/** Separate from the interview chat model; implementations must preserve input order. */
public interface PassageEmbeddingClient {
    String modelKey();
    List<double[]> embed(List<String> texts);
}
