package com.first.genProject.embedding;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import com.first.genProject.exception.DocumentSearchException;
import static com.first.genProject.exception.DocumentSearchException.Reason.*;


public class OllamaPassageEmbeddingClient implements PassageEmbeddingClient {
    private final boolean enabled;
    private final String model;
    private final String modelKey;
    private final RestClient client;

    public OllamaPassageEmbeddingClient(boolean enabled, String baseUrl, String model) {
        this(enabled, baseUrl, model, builder());
    }

    // Allows tests to bind a mock HTTP server without opening network sockets.
    OllamaPassageEmbeddingClient(boolean enabled, String baseUrl, String model, RestClient.Builder builder) {
        URI uri = URI.create(baseUrl);
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || model == null || model.isBlank() || model.length() > 200) {
            throw new IllegalArgumentException("Invalid document embedding endpoint or model configuration");
        }
        this.enabled = enabled;
        this.model = model;
        this.modelKey = "ollama:" + model + ":" + endpointHash(baseUrl.replaceAll("/+$", ""));
        this.client = builder.baseUrl(baseUrl).build();
    }

    @Override
    public String modelKey() {
        if (!enabled) throw new DocumentSearchException(DISABLED);
        return modelKey;
    }

    @Override
    public List<double[]> embed(List<String> texts) {
        modelKey();
        try {
            var response = client.post().uri("/api/embed").contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("model", model, "input", texts, "truncate", false))
                    .retrieve().body(EmbeddingResponse.class);
            if (response == null) throw new DocumentSearchException(PROVIDER_FAILURE);
            return EmbeddingVectors.normalize(response.embeddings(), texts.size());
        } catch (RuntimeException exception) {
            // Never return raw provider bodies, endpoints, or exception text to API callers.
            throw new DocumentSearchException(PROVIDER_FAILURE);
        }
    }

    private static RestClient.Builder builder() {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(60));
        return RestClient.builder().requestFactory(factory);
    }

    private static String endpointHash(String endpoint) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(endpoint.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    record EmbeddingResponse(List<double[]> embeddings) { }
}
