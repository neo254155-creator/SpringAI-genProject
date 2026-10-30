package com.first.genProject;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import tools.jackson.databind.json.JsonMapper;
import com.first.genProject.persistence.StudyDocumentRepository;
import com.first.genProject.persistence.StudyPassageRepository;

import com.first.genProject.support.StubChatModel;

/** Exercises actual MVC wiring while replacing the provider with a local model. */
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.properties")
@SpringBootTest(properties = {
        "spring.ai.openai.api-key=offline-test-key",
        "spring.ai.openai.chat.model=offline-test-model"
})
@AutoConfigureMockMvc
@Import(GenProjectApplicationTests.OfflineAiConfiguration.class)
class GenProjectApplicationTests {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private StubChatModel stub;

    @Autowired
    private ChatModel activeModel;

    @Autowired private StudyDocumentRepository documents;
    @Autowired private StudyPassageRepository passages;

    @BeforeEach
    void resetStub() {
        stub.calls = 0;
        stub.prompt = null;
        stub.response = "Offline test response";
        stub.failure = null;
    }

    @Test
    void getChatUsesUserInputAndConfiguredModel() throws Exception {
        assertSame(stub, activeModel);
        mvc.perform(get("/chat").param("q", "Explain Java interfaces"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("Offline test response"));
        assertEquals(1, stub.calls);
        assertEquals("Explain Java interfaces", stub.prompt.getUserMessage().getText().trim());
        assertEquals("offline-test-model", stub.prompt.getOptions().getModel());
        assertEquals(1000, stub.prompt.getOptions().getMaxTokens());
    }

    @Test
    void postChatUsesJsonInput() throws Exception {
        mvc.perform(post("/chat").contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"Explain Spring beans\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Offline test response"));
        assertEquals("Explain Spring beans", stub.prompt.getUserMessage().getText().trim());
    }

    @Test
    void interviewUsesSameConfiguredModel() throws Exception {
        mvc.perform(post("/interview/questions").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"track":"Java","topic":"Spring Boot","level":"intermediate"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question").value("Offline test response"));
        assertEquals("offline-test-model", stub.prompt.getOptions().getModel());
        assertTrue(stub.prompt.getUserMessage().getText().contains("Spring Boot"));
    }

    @Test
    void invalidInputUsesProblemResponseWithoutCallingProvider() throws Exception {
        mvc.perform(post("/chat").contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.instance").value("/chat"));
        mvc.perform(post("/interview/questions").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        assertEquals(0, stub.calls);
    }

    @Test
    void malformedJsonAndMissingQueryReturn400() throws Exception {
        mvc.perform(post("/chat").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        mvc.perform(get("/chat"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        assertEquals(0, stub.calls);
    }

    @Test
    void unsupportedMethodAndContentTypeKeepHttpStatus() throws Exception {
        mvc.perform(put("/chat"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
        mvc.perform(post("/chat").contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));
        assertEquals(0, stub.calls);
    }

    @Test
    void textChatRejectsJsonOnlyAcceptHeader() throws Exception {
        mvc.perform(get("/chat").param("q", "Explain Java").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotAcceptable());
        assertEquals(0, stub.calls);
    }

    @Test
    void providerFailureHasSafeProblemResponse() throws Exception {
        stub.failure = new IllegalStateException("private provider details");
        mvc.perform(get("/chat").param("q", "Explain Java"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("AI_PROVIDER_ERROR"))
                .andExpect(jsonPath("$.detail").value(
                        "The AI provider could not complete the request. Please try again."))
                .andExpect(jsonPath("$.instance").value("/chat"));
    }

    @Test
    void emptyProviderOutputReturns502() throws Exception {
        stub.response = " ";
        mvc.perform(post("/chat").contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"Explain Java\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("AI_PROVIDER_ERROR"));
    }

    @Test
    void feedbackReturnsTypedScoresThroughApplicationWiring() throws Exception {
        stub.response = com.first.genProject.support.FeedbackFixtures.VALID_FEEDBACK;
        mvc.perform(post("/interview/feedback").contentType(MediaType.APPLICATION_JSON)
                .content(com.first.genProject.support.FeedbackFixtures.VALID_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(7))
                .andExpect(jsonPath("$.maxScore").value(10))
                .andExpect(jsonPath("$.rubric.completeness.score").value(2));
        assertEquals("offline-test-model", stub.prompt.getOptions().getModel());
        assertEquals(1, stub.calls);
    }

    @Test
    void uploadedNotesRoundTripWithStableOrderedPassagesWithoutCallingAi() throws Exception {
        String text = "a".repeat(1199) + "😀" + "\nSpring beans are managed by the container.";
        var file = new MockMultipartFile("file", "C:\\notes\\spring.md", "text/markdown",
                text.getBytes(StandardCharsets.UTF_8));
        var response = mvc.perform(multipart("/documents").file(file).param("title", " Spring notes "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Spring notes"))
                .andExpect(jsonPath("$.filename").value("spring.md"))
                .andReturn().getResponse();
        String location = response.getHeader("Location");
        mvc.perform(get(location)).andExpect(status().isOk())
                .andExpect(content().json(response.getContentAsString()));
        String body = mvc.perform(get(location + "/passages"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].number").value(1))
                .andExpect(jsonPath("$[1].number").value(2))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        var nodes = JsonMapper.builder().build().readTree(body);
        assertEquals(text, nodes.get(0).get("text").asText() + nodes.get(1).get("text").asText());
        assertNotEquals(nodes.get(0).get("id").asText(), nodes.get(1).get("id").asText());
        mvc.perform(get(location + "/passages")).andExpect(content().json(body));
        mvc.perform(get("/documents").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1));
        assertEquals(0, stub.calls);
    }

    @Test
    void invalidUploadsDoNotPersistDocumentsOrPassages() throws Exception {
        long documentCount = documents.count();
        long passageCount = passages.count();
        var files = java.util.List.of(
                new MockMultipartFile("file", "notes.txt", "text/plain", new byte[0]),
                new MockMultipartFile("file", "notes.pdf", "application/pdf", "pdf".getBytes(StandardCharsets.UTF_8)),
                new MockMultipartFile("file", "notes.txt", "text/plain", new byte[] {(byte) 0xc3, 0x28}),
                new MockMultipartFile("file", "notes.txt", "text/plain", new byte[] {0}),
                new MockMultipartFile("file", "notes.txt", "text/plain", " \n\t".getBytes(StandardCharsets.UTF_8)),
                new MockMultipartFile("file", "notes.txt", "text/plain", new byte[65_537]));
        for (var file : files) {
            mvc.perform(multipart("/documents").file(file).param("title", "Notes"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
        var validFile = new MockMultipartFile("file", "notes.txt", "text/plain", "Java".getBytes(StandardCharsets.UTF_8));
        for (String title : java.util.List.of(" ", "x".repeat(201))) {
            mvc.perform(multipart("/documents").file(validFile).param("title", title))
                    .andExpect(status().isBadRequest());
        }
        assertEquals(documentCount, documents.count());
        assertEquals(passageCount, passages.count());
        assertEquals(0, stub.calls);
    }

    @Test
    void documentEndpointsValidateMissingResourcesAndPagination() throws Exception {
        String unknown = "/documents/" + UUID.randomUUID();
        mvc.perform(get(unknown)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(get(unknown + "/passages")).andExpect(status().isNotFound());
        mvc.perform(get("/documents").param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/documents").param("size", "101")).andExpect(status().isBadRequest());
        mvc.perform(multipart("/documents").param("title", "Notes")).andExpect(status().isBadRequest());
        assertEquals(0, stub.calls);
    }

    @Test
    void documentIndexingReportsDisabledEmbeddingsWithDefaultConfiguration() throws Exception {
        var file = new MockMultipartFile("file", "notes.txt", "text/plain", "Java notes".getBytes(StandardCharsets.UTF_8));
        String location = mvc.perform(multipart("/documents").file(file).param("title", "Notes"))
                .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
        mvc.perform(post(location + "/index")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("EMBEDDINGS_UNAVAILABLE"));
        assertEquals(0, stub.calls);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class OfflineAiConfiguration {
        @Bean
        @Primary
        StubChatModel offlineChatModel() {
            return new StubChatModel();
        }
    }
}
