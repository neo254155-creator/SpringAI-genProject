package com.first.genProject.service;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.client.ChatClient;
import com.first.genProject.dto.*;
import com.first.genProject.exception.*;
import com.first.genProject.support.StubChatModel;

class DocumentAnswerServiceTest {
    private static final UUID DOCUMENT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PASSAGE = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final StubChatModel model = new StubChatModel();
    private final Retrieval retrieval = new Retrieval();
    private final DocumentAnswerService service = new DocumentAnswerService(retrieval, ChatClient.create(model));
    private final DocumentSearchRequest request = new DocumentSearchRequest(" What is an interface? ", null, null);

    private String valid() {
        return "{\"answer\":\"An interface defines a contract.\",\"passageIds\":[\"" + PASSAGE
                + "\"],\"insufficientEvidence\":false}";
    }

    @Test
    void answersWithExactSavedSourcesAndBoundedValidatedRetrieval() {
        model.response = valid();
        var result = service.answer(DOCUMENT, request);
        assertEquals("An interface defines a contract.", result.answer());
        assertFalse(result.insufficientEvidence());
        assertEquals(List.of(retrieval.result.matches().getFirst()), result.sources());
        assertEquals("What is an interface?", retrieval.request.query());
        assertEquals(5, retrieval.request.limit().intValue());
        assertEquals(0.0, retrieval.request.minimumScore().doubleValue());
        assertTrue(model.prompt.getUserMessage().getText().contains(PASSAGE.toString()));
        assertTrue(model.prompt.getUserMessage().getText().contains("Java {interface} defines a contract."));
        assertTrue(model.prompt.getSystemMessage().getText().contains("untrusted data"));
        assertFalse(model.prompt.getSystemMessage().getText().contains("{format}"));
    }

    @Test
    void noMatchesReturnsInsufficientEvidenceWithoutCallingChat() {
        retrieval.result = new DocumentSearchResponse(DOCUMENT, "Notes", "Question?", List.of());
        var result = service.answer(DOCUMENT, request);
        assertTrue(result.insufficientEvidence());
        assertTrue(result.sources().isEmpty());
        assertEquals(0, model.calls);
    }

    @Test
    void modelCanAbstainAndItsUnsupportedProseIsDiscarded() {
        model.response = "{\"answer\":\"An unsupported guess\",\"passageIds\":[],\"insufficientEvidence\":true}";
        var result = service.answer(DOCUMENT, request);
        assertTrue(result.insufficientEvidence());
        assertFalse(result.answer().contains("guess"));
        assertTrue(result.sources().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not json", "{}", "null",
        "{\"answer\":\"Claim\",\"passageIds\":[],\"insufficientEvidence\":false}",
        "{\"answer\":\"Claim\",\"passageIds\":null,\"insufficientEvidence\":false}",
        "{\"answer\":\"Claim\",\"passageIds\":[null],\"insufficientEvidence\":false}",
        "{\"answer\":\"Claim\",\"passageIds\":[\"33333333-3333-3333-3333-333333333333\"],\"insufficientEvidence\":false}",
        "{\"answer\":\"Claim\",\"passageIds\":[\"22222222-2222-2222-2222-222222222222\"],\"insufficientEvidence\":true}",
        "{\"answer\":\" \",\"passageIds\":[\"22222222-2222-2222-2222-222222222222\"],\"insufficientEvidence\":false}"})
    void rejectsMalformedUnsupportedOrContradictoryCitations(String response) {
        model.response = response;
        assertThrows(AiProviderException.class, () -> service.answer(DOCUMENT, request));
    }

    @Test
    void rejectsDuplicateSourcesAndOversizedAnswers() {
        model.response = valid().replace("\"],\"insufficient", "\",\"" + PASSAGE + "\"],\"insufficient");
        assertThrows(AiProviderException.class, () -> service.answer(DOCUMENT, request));
        model.response = valid().replace("An interface defines a contract.", "a".repeat(6001));
        assertThrows(AiProviderException.class, () -> service.answer(DOCUMENT, request));
    }

    @Test
    void invalidRequestsAndRetrievalFailuresDoNotCallChat() {
        assertThrows(InvalidInputException.class, () -> service.answer(DOCUMENT, null));
        assertThrows(InvalidInputException.class, () -> service.answer(DOCUMENT, new DocumentSearchRequest(" ", null, null)));
        assertEquals(0, retrieval.calls);
        retrieval.failure = new DocumentSearchException(DocumentSearchException.Reason.INDEX_NOT_READY);
        assertThrows(DocumentSearchException.class, () -> service.answer(DOCUMENT, request));
        assertEquals(0, model.calls);
    }

    @Test
    void wrapsProviderFailuresWithoutReturningTheirDetails() {
        model.failure = new IllegalStateException("private details");
        assertThrows(AiProviderException.class, () -> service.answer(DOCUMENT, request));
    }

    private static class Retrieval extends DocumentSearchService {
        DocumentSearchResponse result = new DocumentSearchResponse(DOCUMENT, "Notes", "What is an interface?",
                List.of(new DocumentSearchResponse.Match(PASSAGE, 1, "Java {interface} defines a contract.", 0.8)));
        DocumentSearchRequest request;
        RuntimeException failure;
        int calls;
        Retrieval() { super(null, null, null); }
        @Override public DocumentSearchResponse search(UUID id, DocumentSearchRequest request) {
            calls++;
            assertEquals(DOCUMENT, id);
            this.request = request;
            if (failure != null) throw failure;
            return result;
        }
    }
}
