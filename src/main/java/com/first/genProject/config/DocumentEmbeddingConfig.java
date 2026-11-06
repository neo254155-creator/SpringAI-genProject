package com.first.genProject.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.first.genProject.embedding.*;
import com.first.genProject.exception.DocumentSearchException;

@Configuration(proxyBeanMethods = false)
public class DocumentEmbeddingConfig {
    @Bean
    public PassageEmbeddingClient guardedEmbeddingClient(
            @Value("${document-search.embedding.enabled:false}") boolean enabled,
            @Value("${document-search.embedding.provider:openai-compatible}") String provider,
            @Value("${document-search.embedding.base-url:}") String baseUrl,
            @Value("${document-search.embedding.model:}") String model,
            @Value("${document-search.embedding.api-key:}") String apiKey,
            com.first.genProject.service.AiQuota quota) {
        var delegate=passageEmbeddingClient(enabled,provider,baseUrl,model,apiKey);
        return enabled ? new QuotaEmbeddingClient(delegate,quota) : delegate;
    }
    public PassageEmbeddingClient passageEmbeddingClient(
            @Value("${document-search.embedding.enabled:false}") boolean enabled,
            @Value("${document-search.embedding.provider:openai-compatible}") String provider,
            @Value("${document-search.embedding.base-url:}") String baseUrl,
            @Value("${document-search.embedding.model:}") String model,
            @Value("${document-search.embedding.api-key:}") String apiKey) {
        if (!enabled) return new PassageEmbeddingClient() {
            public String modelKey() { throw new DocumentSearchException(DocumentSearchException.Reason.DISABLED); }
            public List<double[]> embed(List<String> texts) { throw new DocumentSearchException(DocumentSearchException.Reason.DISABLED); }
        };
        if (baseUrl.isBlank() || model.isBlank()) {
            throw new IllegalArgumentException("Enabled document embeddings require a base URL and model");
        }
        if (!"openai-compatible".equals(provider)) {
            throw new IllegalArgumentException("Embedding provider must be openai-compatible (Google Gemini)");
        }
        return new HostedPassageEmbeddingClient(baseUrl, model, apiKey);
    }
}
