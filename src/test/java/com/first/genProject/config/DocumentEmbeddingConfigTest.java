package com.first.genProject.config;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import com.first.genProject.embedding.*;
import com.first.genProject.exception.DocumentSearchException;

class DocumentEmbeddingConfigTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(DocumentEmbeddingConfig.class)
            .withBean(com.first.genProject.service.AiQuota.class, () -> org.mockito.Mockito.mock(com.first.genProject.service.AiQuota.class));

    @Test
    void disabledFeatureStartsWithoutProviderCredentials() {
        context.run(c -> {
            assertNull(c.getStartupFailure());
            assertThrows(DocumentSearchException.class, () -> c.getBean(PassageEmbeddingClient.class).embed(List.of("text")));
        });
    }

    @Test
    void propertiesSelectHostedGeminiProvider() {
        context.withPropertyValues("document-search.embedding.enabled=true",
                "document-search.embedding.provider=openai-compatible", "document-search.embedding.base-url=https://generativelanguage.googleapis.com/v1beta/openai",
                "document-search.embedding.model=model", "document-search.embedding.api-key=test-key")
                .run(c -> assertInstanceOf(QuotaEmbeddingClient.class, c.getBean(PassageEmbeddingClient.class)));
    }

    @Test
    void unsupportedProviderFailsAtStartup() {
        context.withPropertyValues("document-search.embedding.enabled=true", "document-search.embedding.provider=unsupported",
                "document-search.embedding.base-url=https://generativelanguage.googleapis.com/v1beta/openai",
                "document-search.embedding.model=gemini-embedding-001", "document-search.embedding.api-key=test-key")
                .run(c -> assertNotNull(c.getStartupFailure()));
    }

    @Test
    void enabledButIncompleteSettingsFailClearlyAtStartup() {
        context.withPropertyValues("document-search.embedding.enabled=true")
                .run(c -> assertNotNull(c.getStartupFailure()));
        context.withPropertyValues("document-search.embedding.enabled=true", "document-search.embedding.base-url=https://generativelanguage.googleapis.com/v1beta/openai",
                "document-search.embedding.model=model").run(c -> assertNotNull(c.getStartupFailure()));
    }
}
