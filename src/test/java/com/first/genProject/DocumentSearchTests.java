package com.first.genProject;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;
import com.first.genProject.embedding.PassageEmbeddingClient;
import com.first.genProject.exception.DocumentSearchException;
import com.first.genProject.persistence.*;
import com.first.genProject.support.StubChatModel;

@SpringBootTest(properties = {"spring.ai.openai.api-key=offline-test-key", "spring.ai.openai.chat.model=offline-test-model"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.properties", properties =
        "spring.datasource.url=jdbc:h2:mem:document-search-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Import(DocumentSearchTests.OfflineConfiguration.class)
class DocumentSearchTests {
    @Autowired MockMvc mvc;
    @Autowired FakeEmbeddings embeddings;
    @Autowired StubChatModel chat;
    @Autowired StudyDocumentRepository documents;
    @Autowired StudyPassageRepository passages;
    @Autowired DocumentSearchIndexRepository indexes;
    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void reset() {
        indexes.deleteAll();
        passages.deleteAll();
        documents.deleteAll();
        embeddings.key = "test-model-v1";
        embeddings.calls = 0;
        embeddings.failure = false;
        embeddings.invalid = false;
        embeddings.dimensionChange = false;
        embeddings.oppositeQuery = false;
        chat.calls = 0;
    }

    @Test
    void indexesAndRanksOnlyRequestedDocumentsPassagesWithExactSourceText() throws Exception {
        String path = upload();
        upload(); // A second document must never contribute passages to this result.
        mvc.perform(post(path + "/index")).andExpect(status().isOk())
                .andExpect(jsonPath("$.passageCount").value(3)).andExpect(jsonPath("$.dimensions").value(2));
        String result = search(path, "{\"query\":\"container-managed objects\",\"limit\":2,\"minimumScore\":0.5}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matches.length()").value(2))
                .andExpect(jsonPath("$.matches[0].number").value(2))
                .andExpect(jsonPath("$.matches[0].similarity").value(1.0))
                .andExpect(jsonPath("$.matches[0].text").value("B".repeat(1200)))
                .andExpect(jsonPath("$.matches[1].number").value(3))
                .andReturn().getResponse().getContentAsString();
        var id = UUID.fromString(path.substring("/documents/".length()));
        var source = passages.findByDocument_IdOrderByPassageNumberAsc(id);
        assertEquals(source.get(1).getId().toString(), json.readTree(result).get("matches").get(0).get("passageId").asText());
        assertEquals(2, embeddings.calls);
        assertEquals(0, chat.calls);
        search(path, "{\"query\":\"q\",\"minimumScore\":1}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.matches.length()").value(1));
    }

    @Test
    void returnsNoMatchesWhenNothingMeetsTheSimilarityThreshold() throws Exception {
        String path = upload();
        mvc.perform(post(path + "/index")).andExpect(status().isOk());
        embeddings.oppositeQuery = true;
        search(path, "{\"query\":\"unrelated question\",\"minimumScore\":0}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.matches.length()").value(0));
    }

    @Test
    void reindexReplacesOneSnapshotAndProviderFailurePreservesPreviousIndex() throws Exception {
        String path = upload();
        mvc.perform(post(path + "/index")).andExpect(status().isOk());
        mvc.perform(post(path + "/index")).andExpect(status().isOk());
        assertEquals(1, indexes.count());
        var before = indexes.findAll().getFirst();
        embeddings.failure = true;
        mvc.perform(post(path + "/index")).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("EMBEDDING_PROVIDER_ERROR"));
        var after = indexes.findAll().getFirst();
        assertEquals(before.getVectorsJson(), after.getVectorsJson());
        assertEquals(before.getIndexedAt(), after.getIndexedAt());
        embeddings.failure = false;
        search(path, "{\"query\":\"q\"}").andExpect(status().isOk());
    }

    @Test
    void malformedEmbeddingsNeverCreateAnIndex() throws Exception {
        String path = upload();
        embeddings.invalid = true;
        mvc.perform(post(path + "/index")).andExpect(status().isBadGateway());
        assertEquals(0, indexes.count());
    }

    @Test
    void unindexedAndChangedModelRequireReindexing() throws Exception {
        String path = upload();
        search(path, "{\"query\":\"q\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOCUMENT_INDEX_NOT_READY"));
        assertEquals(0, embeddings.calls);
        mvc.perform(post(path + "/index")).andExpect(status().isOk());
        embeddings.key = "test-model-v2";
        search(path, "{\"query\":\"q\"}").andExpect(status().isConflict());
        assertEquals(1, embeddings.calls);
        mvc.perform(post(path + "/index")).andExpect(status().isOk());
        search(path, "{\"query\":\"q\"}").andExpect(status().isOk());
        embeddings.dimensionChange = true;
        search(path, "{\"query\":\"q\"}").andExpect(status().isConflict());
    }

    @Test
    void invalidRequestsAndUnknownDocumentsMakeNoEmbeddingCalls() throws Exception {
        String path = upload();
        for (String body : List.of("{}", "{\"query\":\" \"}", "{\"query\":\"q\",\"limit\":0}",
                "{\"query\":\"q\",\"limit\":21}", "{\"query\":\"q\",\"minimumScore\":1.1}")) {
            search(path, body).andExpect(status().isBadRequest());
        }
        mvc.perform(post("/documents/" + UUID.randomUUID() + "/index")).andExpect(status().isNotFound());
        search("/documents/" + UUID.randomUUID(), "{\"query\":\"q\"}").andExpect(status().isNotFound());
        assertEquals(0, embeddings.calls);
    }

    private String upload() throws Exception {
        var file = new MockMultipartFile("file", "notes.txt", "text/plain",
                ("A".repeat(1200) + "B".repeat(1200) + "C".repeat(1200)).getBytes(StandardCharsets.UTF_8));
        return mvc.perform(multipart("/documents").file(file).param("title", "Notes"))
                .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
    }

    private org.springframework.test.web.servlet.ResultActions search(String path, String body) throws Exception {
        return mvc.perform(post(path + "/search").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    static class FakeEmbeddings implements PassageEmbeddingClient {
        String key;
        int calls;
        boolean failure, invalid, dimensionChange, oppositeQuery;
        public String modelKey() { return key; }
        public List<double[]> embed(List<String> texts) {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "AI call held a DB transaction");
            calls++;
            if (failure) throw new DocumentSearchException(DocumentSearchException.Reason.PROVIDER_FAILURE);
            if (invalid) return List.of(new double[] {0, 0});
            if (dimensionChange) return List.of(new double[] {1, 0, 0});
            if (oppositeQuery) return List.of(new double[] {-1, -1});
            return texts.size() == 1 ? List.of(new double[] {0, 1})
                    : List.of(new double[] {1, 0}, new double[] {0, 2}, new double[] {1, 1});
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class OfflineConfiguration {
        @Bean @Primary FakeEmbeddings fakeEmbeddings() { return new FakeEmbeddings(); }
        @Bean @Primary StubChatModel offlineChatModel() { return new StubChatModel(); }
    }
}
