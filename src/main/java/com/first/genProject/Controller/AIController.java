package com.first.genProject.Controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.first.genProject.dto.ChatRequest;
import com.first.genProject.dto.ChatResponse;
import com.first.genProject.service.AiService;

@RestController
public class AIController {
    private final AiService aiService;

    public AIController(AiService aiService) {
        this.aiService = aiService;
    }

    @GetMapping(value = "/chat", produces = MediaType.TEXT_PLAIN_VALUE)
    public String chat(@RequestParam("q") String query) {
        return aiService.chat(query);
    }

    @PostMapping(value = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ChatResponse chat(@RequestBody ChatRequest request) {
        return new ChatResponse(aiService.chat(request.message()));
    }
}
