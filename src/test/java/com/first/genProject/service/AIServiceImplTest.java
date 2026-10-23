package com.first.genProject.service;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.client.ChatClient;
import com.first.genProject.exception.*;
import com.first.genProject.support.StubChatModel;

class AIServiceImplTest {
    private final StubChatModel model = new StubChatModel();
    private final AIServiceImpl service = new AIServiceImpl(ChatClient.create(model));

    @Test
    void rendersUserInputAsDataAndLoadsSystemResource() {
        String code = "Explain: public void run() { System.out.println(\"{message}\"); }";
        assertEquals("Stub response", service.chat(code));
        assertEquals(code, model.prompt.getUserMessage().getText().trim());
        assertTrue(model.prompt.getSystemMessage().getText().contains("Java coding assistant"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\n\t"})
    void rejectsBlankInput(String message) {
        assertThrows(InvalidInputException.class, () -> service.chat(message));
        assertEquals(0, model.calls);
    }

    @Test
    void checksLengthBoundary() {
        assertThrows(InvalidInputException.class, () -> service.chat("x".repeat(8001)));
        assertEquals(0, model.calls);
        service.chat("x".repeat(8000));
        assertEquals(1, model.calls);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void rejectsEmptyProviderOutput(String response) {
        model.response = response;
        assertThrows(AiProviderException.class, () -> service.chat("Explain Java"));
    }
}
