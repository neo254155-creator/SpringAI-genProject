package com.first.genProject.embedding;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import com.first.genProject.exception.DocumentSearchException;
import static com.first.genProject.exception.DocumentSearchException.Reason.PROVIDER_FAILURE;

/** For providers implementing the OpenAI-compatible POST /embeddings contract. */
public class HostedPassageEmbeddingClient implements PassageEmbeddingClient {
    private final RestClient client;
    private final String model;
    private final String key;

    public HostedPassageEmbeddingClient(String baseUrl, String model, String apiKey) {
        this(baseUrl, model, apiKey, builder());
    }

    HostedPassageEmbeddingClient(String baseUrl, String model, String apiKey, RestClient.Builder builder) {
        URI uri = URI.create(baseUrl);
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null || apiKey == null || apiKey.isBlank()
                || model == null || model.isBlank() || model.length() > 200) {
            throw new IllegalArgumentException("Hosted embeddings require an HTTPS base URL, model, and API key");
        }
        String endpoint = baseUrl.replaceAll("/+$", "");
        this.model = model;
        this.key = "openai-compatible:" + model + ":" + hash(endpoint);
        this.client = builder.baseUrl(endpoint).defaultHeaders(headers -> headers.setBearerAuth(apiKey)).build();
    }

    @Override public String modelKey() { return key; }

    @Override public List<double[]> embed(List<String> texts) {
        try {
            var response = client.post().uri("/embeddings").contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("model", model, "input", texts, "encoding_format", "float"))
                    .retrieve().body(EmbeddingResponse.class);
            if (response == null || response.data() == null || response.data().size() != texts.size()) fail();
            double[][] ordered = new double[texts.size()][];
            for (var entry : response.data()) {
                if (entry == null || entry.index() == null || entry.index() < 0 || entry.index() >= ordered.length
                        || ordered[entry.index()] != null || entry.embedding() == null) fail();
                ordered[entry.index()] = entry.embedding();
            }
            return EmbeddingVectors.normalize(Arrays.asList(ordered), texts.size());
        } catch (RuntimeException exception) {
            throw new DocumentSearchException(PROVIDER_FAILURE);
        }
    }

    private static void fail() { throw new DocumentSearchException(PROVIDER_FAILURE); }

    private static RestClient.Builder builder() {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(60));
        return RestClient.builder().requestFactory(factory);
    }

    private static String hash(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    record EmbeddingResponse(List<EmbeddingEntry> data) { }
    record EmbeddingEntry(Integer index, double[] embedding) { }
}
