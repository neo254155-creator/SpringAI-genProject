package com.first.genProject.Controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.first.genProject.dto.*;
import com.first.genProject.exception.DocumentSearchException;
import com.first.genProject.service.*;
import com.first.genProject.support.StubChatModel;

class DocumentAnswerControllerTest {
    private static final UUID DOCUMENT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PASSAGE = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final StubChatModel model = new StubChatModel();
    private final Retrieval retrieval = new Retrieval();
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new DocumentAnswerController(
            new DocumentAnswerService(retrieval, ChatClient.create(model))))
            .setControllerAdvice(new ApiExceptionHandler()).build();

    @Test
    void postReturnsAnswerAndRealSourceExcerpt() throws Exception {
        model.response = "{\"answer\":\"An interface defines a contract.\",\"passageIds\":[\""
                + PASSAGE + "\"],\"insufficientEvidence\":false}";
        mvc.perform(post("/documents/{id}/answer", DOCUMENT).contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"What is an interface?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value(DOCUMENT.toString()))
                .andExpect(jsonPath("$.insufficientEvidence").value(false))
                .andExpect(jsonPath("$.sources[0].passageId").value(PASSAGE.toString()))
                .andExpect(jsonPath("$.sources[0].text").value("An interface defines a contract."));
    }

    @Test
    void invalidInputAndMalformedBodiesReturn400BeforeChat() throws Exception {
        for (String body : List.of("{}", "null", "{", "{\"query\":\" \"}", "{\"query\":\"Question\",\"limit\":21}")) {
            mvc.perform(post("/documents/{id}/answer", DOCUMENT).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertEquals(0, model.calls);
    }

    @Test
    void invalidProviderOutputReturnsSafe502() throws Exception {
        model.failure = new IllegalStateException("private provider details");
        mvc.perform(post("/documents/{id}/answer", DOCUMENT).contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"Question?\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("AI_PROVIDER_ERROR"))
                .andExpect(jsonPath("$.detail").value("The AI provider could not complete the request. Please try again."));
    }

    @Test
    void retrievalErrorsKeepTheirExistingStatusWithoutCallingChat() throws Exception {
        retrieval.failure = new DocumentSearchException(DocumentSearchException.Reason.INDEX_NOT_READY);
        mvc.perform(post("/documents/{id}/answer", DOCUMENT).contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"Question?\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOCUMENT_INDEX_NOT_READY"));
        assertEquals(0, model.calls);
    }

    private static class Retrieval extends DocumentSearchService {
        RuntimeException failure;
        Retrieval() { super(null, null, null); }
        @Override public DocumentSearchResponse search(UUID id, DocumentSearchRequest request) {
            if (failure != null) throw failure;
            return new DocumentSearchResponse(id, "Notes", request.query(),
                    List.of(new DocumentSearchResponse.Match(PASSAGE, 1, "An interface defines a contract.", 0.8)));
        }
    }
}
