package com.first.genProject.support;

import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.*;

/** In-memory provider substitute: never performs network requests. */
public class StubChatModel implements ChatModel {
    public int calls;
    public Prompt prompt;
    public String response = "Stub response";
    public RuntimeException failure;

    @Override
    public ChatOptions getOptions() { return ChatOptions.builder().build(); }

    @Override
    public ChatResponse call(Prompt prompt) {
        calls++;
        this.prompt = prompt;
        if (failure != null) throw failure;
        return new ChatResponse(response == null ? List.of()
                : List.of(new Generation(new AssistantMessage(response))));
    }
}
