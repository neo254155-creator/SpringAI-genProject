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

class HostedPassageEmbeddingClientTest {
    @Test
    void usesIndependentKeyAndRestoresInputOrderFromResponseIndexes() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new HostedPassageEmbeddingClient("https://embeddings.example/v1", "embedding-model", "test-embedding-key", builder);
        server.expect(requestTo("https://embeddings.example/v1/embeddings")).andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-embedding-key"))
                .andExpect(content().json("""
                    {"model":"embedding-model","input":["first","second"],"encoding_format":"float"}
                    """))
                .andRespond(withSuccess("""
                    {"data":[{"index":1,"embedding":[0,2]},{"index":0,"embedding":[3,4]}]}
                    """, MediaType.APPLICATION_JSON));
        var vectors = client.embed(List.of("first", "second"));
        assertArrayEquals(new double[] {0.6, 0.8}, vectors.get(0), 0.00001);
        assertArrayEquals(new double[] {0, 1}, vectors.get(1), 0.00001);
        assertFalse(client.modelKey().contains("test-embedding-key"));
        server.verify();
    }

    @Test
    void rejectsMissingDuplicateOrInvalidIndexesAndVectors() {
        for (String body : List.of("{}", "{\"data\":[]}",
                "{\"data\":[{\"embedding\":[1]}]}", "{\"data\":[{\"index\":-1,\"embedding\":[1]}]}",
                "{\"data\":[{\"index\":0,\"embedding\":[0,0]}]}",
                "{\"data\":[{\"index\":0,\"embedding\":[1]},{\"index\":0,\"embedding\":[2]}]}")) {
            var builder = RestClient.builder();
            var server = MockRestServiceServer.bindTo(builder).build();
            var client = new HostedPassageEmbeddingClient("https://embeddings.example/v1", "model", "test-key", builder);
            server.expect(anything()).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
            var inputs = body.contains("[2]") ? List.of("one", "two") : List.of("one");
            assertEquals(DocumentSearchException.Reason.PROVIDER_FAILURE,
                    assertThrows(DocumentSearchException.class, () -> client.embed(inputs)).getReason());
            server.verify();
        }
    }

    @Test
    void providerFailureDoesNotExposeResponseBody() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new HostedPassageEmbeddingClient("https://embeddings.example/v1", "model", "test-key", builder);
        server.expect(anything()).andRespond(withUnauthorizedRequest().body("private-key-details"));
        var error = assertThrows(DocumentSearchException.class, () -> client.embed(List.of("one")));
        assertFalse(error.getMessage().contains("private-key-details"));
        server.verify();
    }
}
