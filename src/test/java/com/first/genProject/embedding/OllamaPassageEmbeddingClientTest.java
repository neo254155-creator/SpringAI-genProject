package com.first.genProject.embedding;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import com.first.genProject.exception.DocumentSearchException;

class OllamaPassageEmbeddingClientTest {
    @Test
    void sendsOrderedBatchWithTruncationDisabledAndNormalizesVectors() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new OllamaPassageEmbeddingClient(true, "http://localhost:11434", "test-model", builder);
        server.expect(requestTo("http://localhost:11434/api/embed")).andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"model":"test-model","input":["beans","threads"],"truncate":false}
                        """))
                .andRespond(withSuccess("{\"embeddings\":[[3,4],[0,2]]}", MediaType.APPLICATION_JSON));
        var vectors = client.embed(List.of("beans", "threads"));
        assertArrayEquals(new double[] {0.6, 0.8}, vectors.get(0), 0.00001);
        assertArrayEquals(new double[] {0, 1}, vectors.get(1), 0.00001);
        server.verify();
    }

    @Test
    void rejectsMalformedResponsesAndHidesProviderErrors() {
        for (String body : List.of("{}", "{\"embeddings\":[]}", "{\"embeddings\":[[0,0]]}",
                "{\"embeddings\":[[1],[1,2]]}", "not JSON")) {
            var builder = RestClient.builder();
            var server = MockRestServiceServer.bindTo(builder).build();
            var client = new OllamaPassageEmbeddingClient(true, "http://localhost:11434", "test", builder);
            server.expect(anything()).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
            var exception = assertThrows(DocumentSearchException.class, () -> client.embed(List.of("text")));
            assertEquals(DocumentSearchException.Reason.PROVIDER_FAILURE, exception.getReason());
            server.verify();
        }
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new OllamaPassageEmbeddingClient(true, "http://localhost:11434", "test", builder);
        server.expect(anything()).andRespond(withServerError().body("private provider error"));
        var exception = assertThrows(DocumentSearchException.class, () -> client.embed(List.of("text")));
        assertFalse(exception.getMessage().contains("private"));
        server.verify();
    }

    @Test
    void disabledClientMakesNoRequests() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new OllamaPassageEmbeddingClient(false, "http://localhost:11434", "test", builder);
        assertEquals(DocumentSearchException.Reason.DISABLED,
                assertThrows(DocumentSearchException.class, () -> client.embed(List.of("text"))).getReason());
        server.verify();
    }

    @Test
    void rejectsNonFiniteAndInconsistentVectors() {
        for (var vectors : List.of(List.of(new double[] {Double.NaN}), List.of(new double[] {Double.POSITIVE_INFINITY}),
                List.of(new double[] {1}, new double[] {1, 2}))) {
            assertThrows(DocumentSearchException.class, () -> EmbeddingVectors.normalize(vectors, vectors.size()));
        }
    }
}
