package com.first.genProject.config;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import com.first.genProject.embedding.*;
import com.first.genProject.exception.DocumentSearchException;

class DocumentEmbeddingConfigTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(DocumentEmbeddingConfig.class);

    @Test
    void disabledFeatureStartsWithoutProviderCredentials() {
        context.run(c -> {
            assertNull(c.getStartupFailure());
            assertThrows(DocumentSearchException.class, () -> c.getBean(PassageEmbeddingClient.class).embed(List.of("text")));
        });
    }

    @Test
    void propertiesSelectHostedOrLocalProvider() {
        context.withPropertyValues("document-search.embedding.enabled=true",
                "document-search.embedding.provider=openai-compatible", "document-search.embedding.base-url=https://example.com/v1",
                "document-search.embedding.model=model", "document-search.embedding.api-key=test-key")
                .run(c -> assertInstanceOf(HostedPassageEmbeddingClient.class, c.getBean(PassageEmbeddingClient.class)));
        context.withPropertyValues("document-search.embedding.enabled=true", "document-search.embedding.provider=ollama",
                "document-search.embedding.base-url=http://localhost:11434", "document-search.embedding.model=embeddinggemma")
                .run(c -> assertInstanceOf(OllamaPassageEmbeddingClient.class, c.getBean(PassageEmbeddingClient.class)));
    }

    @Test
    void enabledButIncompleteSettingsFailClearlyAtStartup() {
        context.withPropertyValues("document-search.embedding.enabled=true")
                .run(c -> assertNotNull(c.getStartupFailure()));
        context.withPropertyValues("document-search.embedding.enabled=true", "document-search.embedding.base-url=https://example.com/v1",
                "document-search.embedding.model=model").run(c -> assertNotNull(c.getStartupFailure()));
    }
}
