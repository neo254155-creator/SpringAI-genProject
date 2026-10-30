package com.first.genProject.embedding;

import java.util.ArrayList;
import java.util.List;
import com.first.genProject.exception.DocumentSearchException;
import static com.first.genProject.exception.DocumentSearchException.Reason.PROVIDER_FAILURE;

public final class EmbeddingVectors {
    private EmbeddingVectors() { }

    public static List<double[]> normalize(List<double[]> vectors, int expectedCount) {
        if (vectors == null || vectors.size() != expectedCount || vectors.isEmpty()) fail();
        int dimensions = vectors.getFirst() == null ? 0 : vectors.getFirst().length;
        if (dimensions < 1 || dimensions > 8192) fail();
        List<double[]> result = new ArrayList<>();
        for (double[] vector : vectors) {
            if (vector == null || vector.length != dimensions) fail();
            double norm = 0;
            for (double value : vector) {
                if (!Double.isFinite(value)) fail();
                norm = Math.hypot(norm, value);
            }
            if (norm == 0 || !Double.isFinite(norm)) fail();
            double[] normalized = new double[dimensions];
            for (int i = 0; i < dimensions; i++) normalized[i] = vector[i] / norm;
            result.add(normalized);
        }
        return result;
    }

    public static double cosine(double[] normalizedLeft, double[] normalizedRight) {
        if (normalizedLeft.length != normalizedRight.length) fail();
        double score = 0;
        for (int i = 0; i < normalizedLeft.length; i++) score += normalizedLeft[i] * normalizedRight[i];
        return Math.max(-1, Math.min(1, score));
    }

    private static void fail() { throw new DocumentSearchException(PROVIDER_FAILURE); }
}
